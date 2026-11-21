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

#ifndef LIBRETRODROID_WSOLASTRETCHER_H
#define LIBRETRODROID_WSOLASTRETCHER_H

#include <cstdint>
#include <vector>

namespace libretrodroid {

/**
 * Musical Time-Domain Harmonic Scaling / WSOLA stereo time-stretcher.
 *
 * Uses SoundTouch-grade musical parameters (64ms-116ms pure unmodulated 1.0x
 * waveform segments joined by 16ms raised-cosine crossfades over a 28ms stereo
 * phase-locked seek window) so chiptune/PCM melodies keep their exact natural
 * pitch, stereo imaging, and crisp timbre without buzzing or dropouts during
 * fast-forward.
 */
class WsolaStretcher {
public:
    WsolaStretcher() = default;
    ~WsolaStretcher() = default;

    void configure(int32_t streamSampleRate);
    void reset();

    void process(
        const int16_t* inputStereo,
        int32_t inputFrames,
        int16_t* outputStereo,
        int32_t outputFrames,
        double nominalSpeed
    );

    int32_t bufferedOutputFrames() const { return outputCount + (primed ? overlapFrames : 0); }
    int32_t drainSmoothToNormal(int16_t* outputStereo, int32_t maxFrames);

private:
    void pushInput(const int16_t* stereo, int32_t frames);
    void popInput(int32_t frames);
    void pushOutputSample(int16_t left, int16_t right);
    int32_t popOutput(int16_t* destStereo, int32_t maxFrames);
    int32_t computeBodyFrames(double ratio) const;
    bool synthesizeOneGrain(double effectiveRatio);
    int32_t findBestGrainOffset(int32_t minPos, int32_t maxPos, int32_t centerPos) const;

private:
    static constexpr int32_t kInputRingCapacityFrames = 65536;
    static constexpr int32_t kOutputFifoCapacityFrames = 16384;

    int32_t sampleRate = 48000;
    int32_t overlapFrames = 768;      // ~16 ms crossfade window
    int32_t seekRadiusFrames = 672;   // +-14 ms (28 ms total seek window, covers down to 36 Hz bass)
    int32_t primeThresholdFrames = 4320; // ~90 ms cushion before starting output
    int32_t targetBufferFrames = 5280;   // ~110 ms steady-state cushion across 60Hz VSYNC bursts

    std::vector<float> crossfadeRise;
    std::vector<float> crossfadeFall;
    std::vector<int16_t> inputRing;
    std::vector<int16_t> outputFifo;
    std::vector<float> overlapRef;    // Stereo natural continuation [overlapFrames * 2]

    int32_t inputHead = 0;
    int32_t inputCount = 0;

    int32_t outputHead = 0;
    int32_t outputCount = 0;

    bool primed = false;
    int32_t nextCenterPos = 0;
    double advanceRemainder = 0.0;

    double emaSpeed = 1.5;
    bool speedInitialized = false;
    int64_t accumInputFrames = 0;
    int64_t accumOutputFrames = 0;
};

} // namespace libretrodroid

#endif // LIBRETRODROID_WSOLASTRETCHER_H
