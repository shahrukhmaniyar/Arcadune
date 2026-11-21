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

#ifndef LIBRETRODROID_AUDIO_H
#define LIBRETRODROID_AUDIO_H

#include <array>
#include <atomic>
#include <unistd.h>
#include <oboe/Oboe.h>
#include <oboe/FifoBuffer.h>

#include "resamplers/linearresampler.h"
#include "resamplers/wsolastretcher.h"

namespace libretrodroid {

class Audio: public oboe::AudioStreamDataCallback, oboe::AudioStreamErrorCallback {
public:
    enum FastForwardAudioMode {
        FF_AUDIO_KEEP_PITCH = 0,
        FF_AUDIO_CLASSIC = 1,
        FF_AUDIO_MUTE = 2,
    };

private:
    struct AudioLatencySettings {
        unsigned bufferSizeInVideoFrames;
        bool useLowLatencyStream;
    };

    const AudioLatencySettings DEFAULT_LATENCY_SETTINGS { 8, false };
    const AudioLatencySettings LOW_LATENCY_SETTINGS { 4, true };

public:
    Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio);
    ~Audio() override = default;

    void start();
    void stop();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream *oldStream, oboe::Result result) override;

public:
    void write(const int16_t *data, size_t frames);
    void setPlaybackSpeed(const double newPlaybackSpeed);
    void setFastForwardAudioMode(int mode);

private:
    static int32_t roundToEven(int32_t x);
    double computeDynamicBufferConversionFactor(double dt);
    int32_t computeAudioBufferSize();
    bool initializeStream();
    std::unique_ptr<Audio::AudioLatencySettings> findBestLatencySettings(bool preferLowLatencyAudio);
    double computeMaximumLatency() const;

private:
    const double kp = 0.006;
    const double ki = 0.00002;
    const double maxp = 0.003;
    const double maxi = 0.02;

    LinearResampler resampler;
    WsolaStretcher wsolaStretcher;
    std::unique_ptr<oboe::FifoBuffer> fifoBuffer = nullptr;
    std::unique_ptr<int16_t[]> temporaryAudioBuffer = nullptr;
    std::unique_ptr<int16_t[]> streamRateScratchBuffer = nullptr;
    int32_t nominalBufferSamples = 0;
    int32_t maxTempStereoFrames = 0;

    oboe::ManagedStream stream = nullptr;
    std::unique_ptr<oboe::LatencyTuner> latencyTuner = nullptr;

    bool startRequested = false;
    int32_t inputSampleRate;
    double contentRefreshRate = 60.0;

    double baseConversionFactor = 1.0;

    double framesToSubmit = 0.0;
    double errorIntegral = 0.0;
    bool ffResamplerPrimed = false;
    float ffPrevL = 0.0f;
    float ffPrevR = 0.0f;
    float ffCurrL = 0.0f;
    float ffCurrR = 0.0f;
    double ffResamplePhase = 0.0;
    bool wasFastForwarding = false;
    int lastFastForwardMode = FF_AUDIO_KEEP_PITCH;

    std::atomic<double> playbackSpeed { 1.0 };
    std::atomic<int> fastForwardAudioMode { FF_AUDIO_KEEP_PITCH };

    std::unique_ptr<AudioLatencySettings> audioLatencySettings;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_AUDIO_H
