/*
 *     Copyright (C) 2026  Arcadune Contributors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "wsolastretcher.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace libretrodroid {

namespace {
constexpr double kPi = 3.14159265358979323846;

inline int16_t clampI16(float v) {
    int32_t rounded = static_cast<int32_t>(std::lrintf(v));
    return static_cast<int16_t>(std::clamp<int32_t>(rounded, -32768, 32767));
}
} // namespace

void WsolaStretcher::configure(int32_t streamSampleRate) {
    sampleRate = std::max<int32_t>(streamSampleRate, 8000);

    // 16 ms raised-cosine crossfade + +-14 ms (28 ms total) harmonic seek window.
    overlapFrames = std::clamp<int32_t>(((sampleRate * 16) / 1000 / 4) * 4, 128, 2048);
    seekRadiusFrames = std::clamp<int32_t>((sampleRate * 14) / 1000, 128, 2048);
    primeThresholdFrames = std::max<int32_t>(overlapFrames * 5, (sampleRate * 90) / 1000);
    targetBufferFrames = std::max<int32_t>(primeThresholdFrames + overlapFrames, (sampleRate * 115) / 1000);

    crossfadeRise.resize(overlapFrames);
    crossfadeFall.resize(overlapFrames);
    for (int32_t k = 0; k < overlapFrames; ++k) {
        double phase = (kPi * (static_cast<double>(k) + 0.5)) / static_cast<double>(overlapFrames);
        float rise = static_cast<float>(0.5 * (1.0 - std::cos(phase)));
        crossfadeRise[k] = rise;
        crossfadeFall[k] = 1.0f - rise;
    }

    inputRing.assign(kInputRingCapacityFrames * 2, 0);
    outputFifo.assign(kOutputFifoCapacityFrames * 2, 0);
    overlapRef.assign(overlapFrames * 2, 0.0f);

    reset();
}

void WsolaStretcher::reset() {
    inputHead = 0;
    inputCount = 0;
    outputHead = 0;
    outputCount = 0;
    primed = false;
    nextCenterPos = 0;
    advanceRemainder = 0.0;
    emaSpeed = 1.5;
    speedInitialized = false;
    accumInputFrames = 0;
    accumOutputFrames = 0;
    std::fill(overlapRef.begin(), overlapRef.end(), 0.0f);
}

void WsolaStretcher::pushInput(const int16_t* stereo, int32_t frames) {
    if (frames <= 0 || stereo == nullptr) {
        return;
    }

    if (frames >= kInputRingCapacityFrames) {
        stereo += (frames - kInputRingCapacityFrames) * 2;
        frames = kInputRingCapacityFrames;
        inputHead = 0;
        inputCount = 0;
    } else if (inputCount + frames > kInputRingCapacityFrames) {
        int32_t overflow = (inputCount + frames) - kInputRingCapacityFrames;
        popInput(overflow);
        nextCenterPos = std::max<int32_t>(0, nextCenterPos - overflow);
    }

    int32_t writePos = (inputHead + inputCount) % kInputRingCapacityFrames;
    int32_t firstChunk = std::min<int32_t>(frames, kInputRingCapacityFrames - writePos);
    std::memcpy(
        &inputRing[writePos * 2],
        stereo,
        static_cast<size_t>(firstChunk * 2) * sizeof(int16_t)
    );
    int32_t secondChunk = frames - firstChunk;
    if (secondChunk > 0) {
        std::memcpy(
            &inputRing[0],
            stereo + firstChunk * 2,
            static_cast<size_t>(secondChunk * 2) * sizeof(int16_t)
        );
    }
    inputCount += frames;
}

void WsolaStretcher::popInput(int32_t frames) {
    int32_t actual = std::clamp<int32_t>(frames, 0, inputCount);
    inputHead = (inputHead + actual) % kInputRingCapacityFrames;
    inputCount -= actual;
}

void WsolaStretcher::pushOutputSample(int16_t left, int16_t right) {
    if (outputCount >= kOutputFifoCapacityFrames) {
        outputHead = (outputHead + 1) % kOutputFifoCapacityFrames;
        outputCount--;
    }
    int32_t writePos = (outputHead + outputCount) % kOutputFifoCapacityFrames;
    outputFifo[writePos * 2] = left;
    outputFifo[writePos * 2 + 1] = right;
    outputCount++;
}

int32_t WsolaStretcher::popOutput(int16_t* destStereo, int32_t maxFrames) {
    int32_t toRead = std::clamp<int32_t>(maxFrames, 0, outputCount);
    if (toRead <= 0) {
        return 0;
    }

    int32_t firstChunk = std::min<int32_t>(toRead, kOutputFifoCapacityFrames - outputHead);
    std::memcpy(
        destStereo,
        &outputFifo[outputHead * 2],
        static_cast<size_t>(firstChunk * 2) * sizeof(int16_t)
    );
    int32_t secondChunk = toRead - firstChunk;
    if (secondChunk > 0) {
        std::memcpy(
            destStereo + firstChunk * 2,
            &outputFifo[0],
            static_cast<size_t>(secondChunk * 2) * sizeof(int16_t)
        );
    }
    outputHead = (outputHead + toRead) % kOutputFifoCapacityFrames;
    outputCount -= toRead;
    return toRead;
}

int32_t WsolaStretcher::computeBodyFrames(double ratio) const {
    // Musical TDHS sequence scaling (SoundTouch-style):
    // ~54ms pure body (70ms sequence) at 2x up to ~104ms pure body (120ms sequence) at 6x-8x
    // so full chiptune/PCM notes and arpeggios stay intact and unmodulated.
    double clamped = std::clamp(ratio, 1.0, 8.0);
    double bodyMs = 44.0 + 10.0 * (clamped - 1.0);
    bodyMs = std::clamp(bodyMs, 44.0, 104.0);
    return std::max<int32_t>(overlapFrames, static_cast<int32_t>((sampleRate * bodyMs) / 1000.0));
}

int32_t WsolaStretcher::findBestGrainOffset(
    int32_t minPos,
    int32_t maxPos,
    int32_t centerPos
) const {
    if (maxPos <= minPos) {
        return minPos;
    }

    float refEnergyCoarse = 0.0f;
    float refEnergyFull = 0.0f;
    for (int32_t k = 0; k < overlapFrames; ++k) {
        float rL = overlapRef[k * 2];
        float rR = overlapRef[k * 2 + 1];
        float e = rL * rL + rR * rR;
        refEnergyFull += e;
        if ((k & 1) == 0) {
            refEnergyCoarse += e;
        }
    }

    // Near silence: keep exact nominal cadence without jitter.
    if (refEnergyFull < static_cast<float>(overlapFrames) * 8.0f) {
        return std::clamp<int32_t>(centerPos, minPos, maxPos);
    }

    const float invRadius = 1.0f / static_cast<float>(std::max<int32_t>(1, seekRadiusFrames));

    auto scoreCoarse = [&](int32_t pos) -> float {
        float dot = 0.0f;
        float candEnergy = 0.0f;
        int32_t ringIdx = (inputHead + pos) % kInputRingCapacityFrames;
        for (int32_t k = 0; k < overlapFrames; k += 2) {
            float cL = static_cast<float>(inputRing[ringIdx * 2]);
            float cR = static_cast<float>(inputRing[ringIdx * 2 + 1]);
            dot += overlapRef[k * 2] * cL + overlapRef[k * 2 + 1] * cR;
            candEnergy += cL * cL + cR * cR;
            ringIdx += 2;
            if (ringIdx >= kInputRingCapacityFrames) {
                ringIdx -= kInputRingCapacityFrames;
            }
        }
        if (candEnergy < static_cast<float>(overlapFrames) * 4.0f) {
            return -2.0f;
        }
        float normCorr = dot / std::sqrt(refEnergyCoarse * candEnergy);
        float distPenalty = 0.01f * static_cast<float>(std::abs(pos - centerPos)) * invRadius;
        return normCorr - distPenalty;
    };

    auto scoreFull = [&](int32_t pos) -> float {
        float dot = 0.0f;
        float candEnergy = 0.0f;
        int32_t ringIdx = (inputHead + pos) % kInputRingCapacityFrames;
        for (int32_t k = 0; k < overlapFrames; ++k) {
            float cL = static_cast<float>(inputRing[ringIdx * 2]);
            float cR = static_cast<float>(inputRing[ringIdx * 2 + 1]);
            dot += overlapRef[k * 2] * cL + overlapRef[k * 2 + 1] * cR;
            candEnergy += cL * cL + cR * cR;
            ringIdx++;
            if (ringIdx >= kInputRingCapacityFrames) {
                ringIdx = 0;
            }
        }
        if (candEnergy < static_cast<float>(overlapFrames) * 8.0f) {
            return -2.0f;
        }
        float normCorr = dot / std::sqrt(refEnergyFull * candEnergy);
        float distPenalty = 0.01f * static_cast<float>(std::abs(pos - centerPos)) * invRadius;
        return normCorr - distPenalty;
    };

    // Stage 1: coarse 4-sample stride scan across the full 28ms harmonic window.
    int32_t bestCoarsePos = std::clamp<int32_t>(centerPos, minPos, maxPos);
    float bestCoarseScore = scoreCoarse(bestCoarsePos);
    for (int32_t pos = minPos; pos <= maxPos; pos += 4) {
        float s = scoreCoarse(pos);
        if (s > bestCoarseScore) {
            bestCoarseScore = s;
            bestCoarsePos = pos;
        }
    }

    // Stage 2: 1-sample full-resolution stereo refinement around bestCoarsePos.
    const int32_t fineMin = std::max<int32_t>(minPos, bestCoarsePos - 5);
    const int32_t fineMax = std::min<int32_t>(maxPos, bestCoarsePos + 5);
    int32_t bestPos = bestCoarsePos;
    float bestScore = scoreFull(bestPos);
    for (int32_t pos = fineMin; pos <= fineMax; ++pos) {
        if (pos == bestCoarsePos) continue;
        float s = scoreFull(pos);
        if (s > bestScore) {
            bestScore = s;
            bestPos = pos;
        }
    }

    return bestPos;
}

bool WsolaStretcher::synthesizeOneGrain(double effectiveRatio) {
    if (!primed) {
        if (inputCount < primeThresholdFrames) {
            return false;
        }

        if (!speedInitialized && accumOutputFrames > 0) {
            double initialMeasured =
                static_cast<double>(accumInputFrames) / static_cast<double>(accumOutputFrames);
            emaSpeed = std::clamp(initialMeasured, 1.05, 8.0);
            speedInitialized = true;
        }

        const int32_t initBody = std::min<int32_t>(
            computeBodyFrames(emaSpeed),
            std::max<int32_t>(0, inputCount - 2 * overlapFrames)
        );
        const int32_t initHopOut = overlapFrames + initBody;
        const int32_t fadeInFrames = std::min<int32_t>(initHopOut, overlapFrames);

        for (int32_t k = 0; k < initHopOut; ++k) {
            int32_t ringIdx = (inputHead + k) % kInputRingCapacityFrames;
            float gain = 1.0f;
            if (k < fadeInFrames) {
                gain = crossfadeRise[(k * overlapFrames) / fadeInFrames];
            }
            pushOutputSample(
                clampI16(static_cast<float>(inputRing[ringIdx * 2]) * gain),
                clampI16(static_cast<float>(inputRing[ringIdx * 2 + 1]) * gain)
            );
        }

        for (int32_t k = 0; k < overlapFrames; ++k) {
            int32_t ringIdx = (inputHead + initHopOut + k) % kInputRingCapacityFrames;
            overlapRef[k * 2] = static_cast<float>(inputRing[ringIdx * 2]);
            overlapRef[k * 2 + 1] = static_cast<float>(inputRing[ringIdx * 2 + 1]);
        }

        const double exactAdvance = initHopOut * effectiveRatio + advanceRemainder;
        const int32_t stepAdvance = std::max<int32_t>(1, static_cast<int32_t>(std::lround(exactAdvance)));
        advanceRemainder = exactAdvance - stepAdvance;

        const int32_t discard = std::clamp<int32_t>(stepAdvance - seekRadiusFrames, 0, inputCount);
        popInput(discard);
        nextCenterPos = std::max<int32_t>(0, stepAdvance - discard);
        primed = true;
        return true;
    }

    // Never drop out during CPU dips: shrink bodyFrames if needed down to minimum 2 * overlapFrames.
    const int32_t minRequiredFrames = 2 * overlapFrames;
    if (inputCount < minRequiredFrames) {
        return false;
    }

    int32_t bodyFrames = computeBodyFrames(effectiveRatio);
    if (inputCount < bodyFrames + 2 * overlapFrames) {
        bodyFrames = std::max<int32_t>(0, inputCount - 2 * overlapFrames);
    }

    const int32_t hopOut = overlapFrames + bodyFrames;
    const int32_t requiredFrames = hopOut + overlapFrames;
    const int32_t maxValidStart = inputCount - requiredFrames;
    const int32_t centerPos = std::clamp<int32_t>(nextCenterPos, 0, maxValidStart);
    const int32_t minPos = std::max<int32_t>(0, centerPos - seekRadiusFrames);
    const int32_t maxPos = std::min<int32_t>(maxValidStart, centerPos + seekRadiusFrames);

    const int32_t bestPos = findBestGrainOffset(minPos, maxPos, centerPos);

    // 1. Crossfade 16ms from previous grain's natural continuation into new phase-aligned grain.
    int32_t ringIdx = (inputHead + bestPos) % kInputRingCapacityFrames;
    for (int32_t k = 0; k < overlapFrames; ++k) {
        const float gL = static_cast<float>(inputRing[ringIdx * 2]);
        const float gR = static_cast<float>(inputRing[ringIdx * 2 + 1]);
        const float outL = crossfadeFall[k] * overlapRef[k * 2] + crossfadeRise[k] * gL;
        const float outR = crossfadeFall[k] * overlapRef[k * 2 + 1] + crossfadeRise[k] * gR;
        pushOutputSample(clampI16(outL), clampI16(outR));
        ringIdx++;
        if (ringIdx >= kInputRingCapacityFrames) {
            ringIdx = 0;
        }
    }

    // 2. Output 100% pure, untouched 1.0x stereo audio for the entire body of the grain.
    for (int32_t m = 0; m < bodyFrames; ++m) {
        pushOutputSample(inputRing[ringIdx * 2], inputRing[ringIdx * 2 + 1]);
        ringIdx++;
        if (ringIdx >= kInputRingCapacityFrames) {
            ringIdx = 0;
        }
    }

    // 3. Save natural continuation following hopOut for the next grain's crossfade.
    for (int32_t k = 0; k < overlapFrames; ++k) {
        overlapRef[k * 2] = static_cast<float>(inputRing[ringIdx * 2]);
        overlapRef[k * 2 + 1] = static_cast<float>(inputRing[ringIdx * 2 + 1]);
        ringIdx++;
        if (ringIdx >= kInputRingCapacityFrames) {
            ringIdx = 0;
        }
    }

    const double exactAdvance = hopOut * effectiveRatio + advanceRemainder;
    const int32_t stepAdvance = std::max<int32_t>(1, static_cast<int32_t>(std::lround(exactAdvance)));
    advanceRemainder = exactAdvance - stepAdvance;

    const int32_t nextCenterInCurrentRing = bestPos + stepAdvance;
    const int32_t discard = std::clamp<int32_t>(nextCenterInCurrentRing - seekRadiusFrames, 0, inputCount);
    popInput(discard);
    nextCenterPos = std::max<int32_t>(0, nextCenterInCurrentRing - discard);

    return true;
}

int32_t WsolaStretcher::drainSmoothToNormal(int16_t* outputStereo, int32_t maxFrames) {
    if (maxFrames <= 0 || outputStereo == nullptr) {
        return 0;
    }
    int32_t written = popOutput(outputStereo, maxFrames);
    if (written < maxFrames && primed) {
        int32_t remain = std::min<int32_t>(maxFrames - written, overlapFrames);
        for (int32_t k = 0; k < remain; ++k) {
            float fade = crossfadeFall[k];
            outputStereo[(written + k) * 2] = clampI16(overlapRef[k * 2] * fade);
            outputStereo[(written + k) * 2 + 1] = clampI16(overlapRef[k * 2 + 1] * fade);
        }
        written += remain;
        primed = false;
    }
    return written;
}

void WsolaStretcher::process(
    const int16_t* inputStereo,
    int32_t inputFrames,
    int16_t* outputStereo,
    int32_t outputFrames,
    double nominalSpeed
) {
    if (outputFrames <= 0 || outputStereo == nullptr) {
        return;
    }
    if (crossfadeRise.empty()) {
        configure(48000);
    }

    const double clampedNominal = std::clamp(nominalSpeed, 1.0, 8.0);
    if (!speedInitialized) {
        // Start conservatively until real CPU throughput is measured so mid-range SoCs
        // never over-drain their input cushion on startup.
        emaSpeed = std::min(clampedNominal, 2.0);
    }

    pushInput(inputStereo, inputFrames);

    accumInputFrames += std::max<int32_t>(0, inputFrames);
    accumOutputFrames += outputFrames;

    // Measure actual emulated audio rate over ~250ms (~15 VSYNCs) to eliminate 60Hz burst ripple.
    const int32_t measurementWindow = std::max<int32_t>(overlapFrames * 8, sampleRate / 4);
    if (accumOutputFrames >= measurementWindow) {
        const double measured = std::clamp(
            static_cast<double>(accumInputFrames) / static_cast<double>(accumOutputFrames),
            0.9,
            9.0
        );
        if (!speedInitialized) {
            emaSpeed = measured;
            speedInitialized = true;
        } else {
            emaSpeed += 0.22 * (measured - emaSpeed);
        }
        accumInputFrames = 0;
        accumOutputFrames = 0;
    }

    // If user explicitly lowered nominal speed (e.g. 6x -> 2x), cap emaSpeed promptly.
    if (emaSpeed > clampedNominal + 0.35 && inputCount < targetBufferFrames * 2) {
        emaSpeed = clampedNominal + 0.35;
    }

    while (outputCount < outputFrames) {
        const double dynamicTarget = std::max<double>(
            targetBufferFrames,
            sampleRate * 0.055 * std::clamp(emaSpeed, 1.2, 4.0)
        );
        const double levelError =
            (static_cast<double>(inputCount) - dynamicTarget) / static_cast<double>(sampleRate * 0.10);

        double effectiveRatio = std::clamp(emaSpeed + 0.30 * levelError, 0.96, 8.8);

        // Low-water guard: if CPU speed dips below emaSpeed, smoothly throttle consumption down
        // toward 1.0x so inputRing never starves or clicks.
        const int32_t lowWaterFrames = overlapFrames * 4;
        if (inputCount < lowWaterFrames) {
            double fill = std::clamp(
                static_cast<double>(inputCount - 2 * overlapFrames) /
                    static_cast<double>(std::max<int32_t>(1, 2 * overlapFrames)),
                0.0,
                1.0
            );
            effectiveRatio = 0.96 + (effectiveRatio - 0.96) * fill;
        }

        if (!synthesizeOneGrain(effectiveRatio)) {
            break;
        }
    }

    int32_t written = popOutput(outputStereo, outputFrames);
    if (written < outputFrames) {
        if (primed) {
            // Extreme OS thread stall (>120ms): smoothly fade out overlapRef over 16ms instead of clicking!
            for (int32_t k = 0; k < overlapFrames; ++k) {
                float fade = crossfadeFall[k];
                pushOutputSample(
                    clampI16(overlapRef[k * 2] * fade),
                    clampI16(overlapRef[k * 2 + 1] * fade)
                );
            }
            std::fill(overlapRef.begin(), overlapRef.end(), 0.0f);
            primed = false;
            written += popOutput(outputStereo + written * 2, outputFrames - written);
        }
        if (written < outputFrames) {
            std::fill_n(outputStereo + written * 2, (outputFrames - written) * 2, int16_t{0});
        }
    }
}

} // namespace libretrodroid
