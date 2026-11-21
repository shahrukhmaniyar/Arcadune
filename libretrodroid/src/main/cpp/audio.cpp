/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#include "log.h"

#include "audio.h"
#include <algorithm>
#include <cmath>
#include <memory>

namespace libretrodroid {

Audio::Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio) {
    LOGI("Audio initialization has been called with input sample rate %d", sampleRate);

    contentRefreshRate = refreshRate;
    inputSampleRate = sampleRate;
    audioLatencySettings = findBestLatencySettings(preferLowLatencyAudio);
    initializeStream();
}

bool Audio::initializeStream() {
    LOGI("Using low latency stream: %d", audioLatencySettings->useLowLatencyStream);

    int32_t audioBufferSize = computeAudioBufferSize();

    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(2);
    builder.setDirection(oboe::Direction::Output);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    if (audioLatencySettings->useLowLatencyStream) {
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder.setFramesPerCallback(audioBufferSize / 10);
    }

    oboe::Result result = builder.openManagedStream(stream);
    if (result == oboe::Result::OK) {
        baseConversionFactor = (double) inputSampleRate / stream->getSampleRate();
        nominalBufferSamples = std::max<int32_t>(audioBufferSize, 256);
        maxTempStereoFrames = std::max<int32_t>(nominalBufferSamples * 8, 16384);
        int32_t fifoCapacitySamples = std::max<int32_t>(nominalBufferSamples * 24, 65536);
        fifoBuffer = std::make_unique<oboe::FifoBuffer>(2, fifoCapacitySamples);
        temporaryAudioBuffer = std::make_unique<int16_t[]>(maxTempStereoFrames * 2);
        streamRateScratchBuffer = std::make_unique<int16_t[]>(maxTempStereoFrames * 2);
        wsolaStretcher.configure(stream->getSampleRate());
        framesToSubmit = 0.0;
        errorIntegral = 0.0;
        ffResamplerPrimed = false;
        ffPrevL = 0.0f;
        ffPrevR = 0.0f;
        ffCurrL = 0.0f;
        ffCurrR = 0.0f;
        ffResamplePhase = 0.0;
        wasFastForwarding = false;
        latencyTuner = std::make_unique<oboe::LatencyTuner>(*stream);
        return true;
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
        stream = nullptr;
        latencyTuner = nullptr;
        return false;
    }
}

std::unique_ptr<Audio::AudioLatencySettings> Audio::findBestLatencySettings(bool preferLowLatencyAudio) {
    if (oboe::AudioStreamBuilder::isAAudioRecommended() && preferLowLatencyAudio) {
        return std::make_unique<AudioLatencySettings>(LOW_LATENCY_SETTINGS);
    } else {
        return std::make_unique<AudioLatencySettings>(DEFAULT_LATENCY_SETTINGS);
    }
}

int32_t Audio::computeAudioBufferSize() {
    double maxLatency = computeMaximumLatency();
    LOGI("Average audio latency set to: %f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    return roundToEven(inputSampleRate / sampleRateDivisor);
}

double Audio::computeMaximumLatency() const {
    double maxLatency = (audioLatencySettings->bufferSizeInVideoFrames / contentRefreshRate) * 1000;
    return std::max(maxLatency, 32.0);
}

void Audio::start() {
    startRequested = true;
    if (stream != nullptr)
        stream->requestStart();
}

void Audio::stop() {
    startRequested = false;
    if (stream != nullptr)
        stream->requestStop();
}

void Audio::write(const int16_t *data, size_t frames) {
    fifoBuffer->write(data, frames * 2);
}

void Audio::setPlaybackSpeed(const double newPlaybackSpeed) {
    playbackSpeed.store(newPlaybackSpeed, std::memory_order_relaxed);
}

void Audio::setFastForwardAudioMode(int mode) {
    int clamped = std::clamp<int>(mode, FF_AUDIO_KEEP_PITCH, FF_AUDIO_MUTE);
    fastForwardAudioMode.store(clamped, std::memory_order_relaxed);
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    auto outputArray = reinterpret_cast<int16_t *>(audioData);
    if (numFrames <= 0 || fifoBuffer == nullptr) {
        return oboe::DataCallbackResult::Continue;
    }

    const double speed = playbackSpeed.load(std::memory_order_relaxed);
    const int mode = fastForwardAudioMode.load(std::memory_order_relaxed);

    if (speed <= 1.01) {
        if (wasFastForwarding) {
            ffResamplerPrimed = false;
            ffResamplePhase = 0.0;
            framesToSubmit = 0.0;
            errorIntegral = 0.0;

            int32_t targetSamples = nominalBufferSamples / 2;
            while (static_cast<int32_t>(fifoBuffer->getFullFramesAvailable()) > targetSamples + 2) {
                int32_t excessStereoFrames =
                    (static_cast<int32_t>(fifoBuffer->getFullFramesAvailable()) - targetSamples) / 2;
                int32_t chunkFrames = std::min<int32_t>(excessStereoFrames, maxTempStereoFrames);
                if (chunkFrames <= 0) break;
                fifoBuffer->readNow(temporaryAudioBuffer.get(), chunkFrames * 2);
            }

            // If fifoBuffer was just drained by KEEP_PITCH mode, bridge the 1-VSYNC refill gap
            // seamlessly from wsolaStretcher's 1.0x output queue before resetting it.
            int32_t availSamples = static_cast<int32_t>(fifoBuffer->getFullFramesAvailable());
            int32_t neededSamples = std::max<int32_t>(2, static_cast<int32_t>(numFrames * baseConversionFactor * 2));
            if (availSamples < neededSamples && wsolaStretcher.bufferedOutputFrames() > 0) {
                int32_t bridged = wsolaStretcher.drainSmoothToNormal(outputArray, numFrames);
                if (bridged < numFrames) {
                    std::fill_n(outputArray + bridged * 2, (numFrames - bridged) * 2, int16_t{0});
                }
                if (wsolaStretcher.bufferedOutputFrames() == 0) {
                    wsolaStretcher.reset();
                    wasFastForwarding = false;
                }
                latencyTuner->tune();
                return oboe::DataCallbackResult::Continue;
            }

            wsolaStretcher.reset();
            wasFastForwarding = false;
        }

        double dynamicBufferFactor = computeDynamicBufferConversionFactor(0.001 * numFrames);
        double finalConversionFactor = baseConversionFactor * dynamicBufferFactor * speed;

        // When using low-latency stream, numFrames is very low (~100) and the dynamic buffer scaling doesn't work with rounding.
        // By keeping track of the "fractional" frames we can keep the error smaller.
        framesToSubmit += numFrames * finalConversionFactor;
        int32_t currentFramesToSubmit = std::clamp<int32_t>(
            static_cast<int32_t>(std::lround(framesToSubmit)),
            0,
            maxTempStereoFrames
        );
        framesToSubmit -= currentFramesToSubmit;

        if (currentFramesToSubmit > 0) {
            fifoBuffer->readNow(temporaryAudioBuffer.get(), currentFramesToSubmit * 2);
            resampler.resample(temporaryAudioBuffer.get(), currentFramesToSubmit, outputArray, numFrames);
        } else {
            std::fill_n(outputArray, numFrames * 2, int16_t{0});
        }
    } else {
        wasFastForwarding = true;
        if (mode != lastFastForwardMode) {
            wsolaStretcher.reset();
            ffResamplerPrimed = false;
            ffResamplePhase = 0.0;
            framesToSubmit = 0.0;
            lastFastForwardMode = mode;
        }

        if (mode == FF_AUDIO_MUTE) {
            int32_t availStereoFrames = static_cast<int32_t>(fifoBuffer->getFullFramesAvailable()) / 2;
            while (availStereoFrames > 0) {
                int32_t chunkFrames = std::min<int32_t>(availStereoFrames, maxTempStereoFrames);
                fifoBuffer->readNow(temporaryAudioBuffer.get(), chunkFrames * 2);
                availStereoFrames -= chunkFrames;
            }
            std::fill_n(outputArray, numFrames * 2, int16_t{0});
        } else if (mode == FF_AUDIO_CLASSIC) {
            double dynamicBufferFactor = computeDynamicBufferConversionFactor(0.001 * numFrames);
            double finalConversionFactor = baseConversionFactor * dynamicBufferFactor * speed;
            framesToSubmit += numFrames * finalConversionFactor;
            int32_t wantedFrames = static_cast<int32_t>(std::lround(framesToSubmit));
            framesToSubmit -= wantedFrames;

            int32_t availStereoFrames = static_cast<int32_t>(fifoBuffer->getFullFramesAvailable()) / 2;
            int32_t boundedFrames = availStereoFrames > 0 ? std::min(wantedFrames, availStereoFrames) : 0;
            int32_t currentFramesToSubmit = std::clamp<int32_t>(boundedFrames, 0, maxTempStereoFrames);

            if (currentFramesToSubmit > 0) {
                fifoBuffer->readNow(temporaryAudioBuffer.get(), currentFramesToSubmit * 2);
                resampler.resample(temporaryAudioBuffer.get(), currentFramesToSubmit, outputArray, numFrames);
            } else {
                std::fill_n(outputArray, numFrames * 2, int16_t{0});
            }
        } else {
            // FF_AUDIO_KEEP_PITCH: continuously resample core audio to stream sample rate at
            // phase-continuous 1.0x pitch across callbacks, then compress time in WSOLA/TDHS.
            const double step = std::max(baseConversionFactor, 0.05);
            int32_t availStereoFrames = static_cast<int32_t>(fifoBuffer->getFullFramesAvailable()) / 2;
            int32_t maxInputForScratch = static_cast<int32_t>(
                std::floor((maxTempStereoFrames - 4) * step)
            );
            int32_t readLimit = std::max<int32_t>(1, std::min(maxTempStereoFrames, maxInputForScratch));
            int32_t readStereoFrames = std::clamp<int32_t>(availStereoFrames, 0, readLimit);
            int32_t streamInFrames = 0;

            if (readStereoFrames > 0) {
                int32_t actualReadSamples = fifoBuffer->read(temporaryAudioBuffer.get(), readStereoFrames * 2);
                int32_t actualFrames = actualReadSamples / 2;
                const int16_t* src = temporaryAudioBuffer.get();
                int16_t* dst = streamRateScratchBuffer.get();

                for (int32_t i = 0; i < actualFrames; ++i) {
                    const float nextL = static_cast<float>(src[i * 2]);
                    const float nextR = static_cast<float>(src[i * 2 + 1]);
                    if (!ffResamplerPrimed) {
                        ffPrevL = nextL;
                        ffPrevR = nextR;
                        ffCurrL = nextL;
                        ffCurrR = nextR;
                        ffResamplePhase = 0.0;
                        ffResamplerPrimed = true;
                    } else {
                        ffPrevL = ffCurrL;
                        ffPrevR = ffCurrR;
                        ffCurrL = nextL;
                        ffCurrR = nextR;
                        ffResamplePhase -= 1.0;
                    }

                    while (ffResamplePhase < 1.0 && streamInFrames < maxTempStereoFrames) {
                        const float t = static_cast<float>(std::clamp(ffResamplePhase, 0.0, 1.0));
                        const float outL = ffPrevL + t * (ffCurrL - ffPrevL);
                        const float outR = ffPrevR + t * (ffCurrR - ffPrevR);
                        dst[streamInFrames * 2] = static_cast<int16_t>(
                            std::clamp<int32_t>(static_cast<int32_t>(std::lrintf(outL)), -32768, 32767)
                        );
                        dst[streamInFrames * 2 + 1] = static_cast<int16_t>(
                            std::clamp<int32_t>(static_cast<int32_t>(std::lrintf(outR)), -32768, 32767)
                        );
                        streamInFrames++;
                        ffResamplePhase += step;
                    }
                }
            }

            wsolaStretcher.process(
                streamRateScratchBuffer.get(),
                streamInFrames,
                outputArray,
                numFrames,
                speed
            );
        }
    }

    latencyTuner->tune();

    return oboe::DataCallbackResult::Continue;
}

// To prevent audio buffer overruns or underruns we set up a PI controller. The idea is to run the
// audio slower when the buffer is empty and faster when it's full.
double Audio::computeDynamicBufferConversionFactor(double dt) {
    double framesCapacityInBuffer = nominalBufferSamples > 0
        ? static_cast<double>(nominalBufferSamples)
        : static_cast<double>(fifoBuffer->getBufferCapacityInFrames());
    double framesAvailableInBuffer = fifoBuffer->getFullFramesAvailable();

    // Error is represented by normalized distance to half buffer utilization. Range [-1.0, 1.0]
    double errorMeasure = (framesCapacityInBuffer - 2.0f * framesAvailableInBuffer) / framesCapacityInBuffer;

    errorIntegral += errorMeasure * dt;

    // Wikipedia states that human ear resolution is around 3.6 Hz within the octave of 1000–2000 Hz.
    // This changes continuously, so we should try to keep it a very low value.
    double proportionalAdjustment = std::clamp(kp * errorMeasure, -maxp, maxp);

    // Ki is a lot lower, so it's safe if it exceeds the ear threshold. Hopefully convergence will
    // be slow enough to be not perceptible. We need to battle test this value.
    double integralAdjustment = std::clamp(ki * errorIntegral, -maxi, maxi);

    double finalAdjustment = proportionalAdjustment + integralAdjustment;

    LOGD("Audio speed adjustments (p: %f) (i: %f)", proportionalAdjustment, integralAdjustment);

    return 1.0 - (finalAdjustment);
}

int32_t Audio::roundToEven(int32_t x) {
    return (x / 2) * 2;
}

void Audio::onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) {
    AudioStreamErrorCallback::onErrorAfterClose(oldStream, result);
    LOGI("Stream error in oboe::onErrorAfterClose %s", oboe::convertToText(result));

    if (result != oboe::Result::ErrorDisconnected)
        return;

    initializeStream();
    if (startRequested) {
        start();
    }
}

} //namespace libretrodroid
