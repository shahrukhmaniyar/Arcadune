/*
 *     Copyright (C) 2020  Filippo Scognamiglio
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

#define MODULE_NAME_CORE "Libretro Core"

#include <utility>
#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <cstdarg>
#include <atomic>
#include <chrono>
#include <EGL/egl.h>
#include <unordered_map>
#include <algorithm>

#include "libretro.h"
#include "log.h"
#include "environment.h"
#include "vfs/vfs.h"
#include "microphone/microphoneinterface.h"

extern "C" {
#include "rc_libretro.h"
}

namespace {

// Supplemental Hardcore core-option restrictions covering shipped cores beyond upstream rc_libretro.c.
const rc_disallowed_setting_t kArcaduneSupplementDisallowedSettings[] = {
    { "dosbox_pure_strict_mode", "false" },
    { "dosbox_pure_savestate", "!disabled" },
    { "fbneo-diagnostic-input", "!None" },
    { "fbneo-cpu-speed-adjust", ",?%,??%" },
    { "opera_cpu_overclock", "0.*" },
    { "picodrive_overclk68k", "-*" },
    { "fmsx_video_mode", "PAL" },
    { "vice_mapper_warp_mode", "!---" },
    { "vice_mapper_datasette_rewind", "!---" },
    { "pce_fast_disable_softreset", "disabled" },
    { "pce_disable_softreset", "disabled" },
    { "ppsspp_cheats", "enabled" },
    { "fceumm_game_genie", "!disabled" },
    { "genesis_plus_gx_lock_on", ",action replay (pro),game genie" },
    { "armgba_cpu_clock", "<100" },
    { "armgba_cpu_clock_boot", "<100" },
    { "armgba_action_replay_game", "!none" },
    { "armgba_game_genie_code_*", "!none" },
    { "armgba_cheat_import", "!off" },
    { "armgba_action_replay_hold", "!off" },
    { "armgba_action_replay_press", "!off" },
    { "bsnes_region", "pal" },
    { "supafaust_region", ",pal,force_pal" },
    { "nestopia_favored_system", ",pal,dendy" },
    { "gearsystem_timing", "PAL (50 Hz)" },
    { "gearsystem_region", ",PAL*,Europe*" },
    { "blastem_region", ",PAL*,Europe*" },
    { "swanstation_CDROM_LoadImagePatches", "true" },
    { "swanstation_Console_Region", "PAL" },
    { "swanstation_Console_Enable8MBRAM", "true" },
    { "beetle_saturn_region", ",Europe,Asia (PAL)" },
    { "beetle_saturn_cart", "Action Replay*" },
    { "vbam_layer_*", "disabled" },
    { nullptr, nullptr }
};

struct MandatoryHardcoreDefault {
    const char* key;
    const char* safeValue;
};

const MandatoryHardcoreDefault kMandatoryHardcoreDefaults[] = {
    { "dosbox_pure_strict_mode", "true" },
    { "dosbox_pure_savestate", "disabled" },
    { "fbneo-diagnostic-input", "None" },
    { "fbneo-cpu-speed-adjust", "100%" },
    { "fbneo-allow-patched-romsets", "disabled" },
    { "fbneo-neogeo-mode", "MVS Asia/Europe" },
    { "opera_cpu_overclock", "1.0x (12.50Mhz)" },
    { "picodrive_overclk68k", "disabled" },
    { "picodrive_region", "Auto" },
    { "fmsx_video_mode", "NTSC" },
    { "vice_autostart", "enabled" },
    { "vice_reset", "autostart" },
    { "vice_mapper_warp_mode", "---" },
    { "vice_mapper_datasette_rewind", "---" },
    { "pce_fast_disable_softreset", "enabled" },
    { "pce_disable_softreset", "enabled" },
    { "ppsspp_cheats", "disabled" },
    { "fceumm_game_genie", "disabled" },
    { "fceumm_region", "Auto" },
    { "mesen_region", "Auto" },
    { "nestopia_favored_system", "auto" },
    { "genesis_plus_gx_lock_on", "disabled" },
    { "genesis_plus_gx_region_detect", "auto" },
    { "snes9x_gfx_clip", "enabled" },
    { "snes9x_gfx_transp", "enabled" },
    { "snes9x_layer_*", "enabled" },
    { "snes9x_region", "auto" },
    { "bsnes_region", "auto" },
    { "supafaust_region", "auto" },
    { "gearsystem_timing", "Auto" },
    { "gearsystem_region", "Auto" },
    { "blastem_region", "auto" },
    { "pcsx_rearmed_psxclock", "auto" },
    { "pcsx_rearmed_region", "auto" },
    { "reicast_sh4clock", "200" },
    { "melonds_console_mode", "ds" },
    { "armgba_cpu_clock", "100" },
    { "armgba_cpu_clock_boot", "100" },
    { "armgba_action_replay_game", "none" },
    { "armgba_game_genie_code_*", "none" },
    { "armgba_cheat_import", "off" },
    { "armgba_action_replay_hold", "off" },
    { "armgba_action_replay_press", "off" },
    { "vbam_layer_*", "enabled" },
    { "beetle_psx_cpu_freq_scale", "100%(native)" },
    { "swanstation_CPU_Overclock", "100" },
    { "swanstation_CDROM_LoadImagePatches", "false" },
    { "swanstation_Console_Region", "Auto" },
    { "swanstation_Console_Enable8MBRAM", "false" },
    { "beetle_saturn_region", "Auto Detect" },
    { "beetle_saturn_cart", "Auto Detect" }
};

bool matchesMandatoryKey(const char* pattern, const std::string& key) {
    if (pattern == nullptr) {
        return false;
    }
    const size_t len = strlen(pattern);
    if (len > 0 && pattern[len - 1] == '*') {
        return key.compare(0, len - 1, pattern, len - 1) == 0;
    }
    return key == pattern;
}

std::vector<std::string> parseOptionCandidates(const std::string& description) {
    std::vector<std::string> options;
    auto semi = description.find(';');
    if (semi == std::string::npos || semi + 2 > description.size()) {
        return options;
    }
    size_t pos = semi + 2;
    while (pos <= description.size()) {
        auto bar = description.find('|', pos);
        if (bar == std::string::npos) {
            options.push_back(description.substr(pos));
            break;
        }
        options.push_back(description.substr(pos, bar - pos));
        pos = bar + 1;
    }
    return options;
}

} // namespace

void Environment::initialize(
    const std::string &requiredSystemDirectory,
    const std::string &requiredSavesDirectory,
    retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
) {
    callback_get_current_framebuffer = required_callback_get_current_framebuffer;
    systemDirectory = requiredSystemDirectory;
    savesDirectory = requiredSavesDirectory;
}

void Environment::prepareForCoreReload() {
    hw_context_reset = nullptr;
    hw_context_destroy = nullptr;
    retro_disk_control_callback = nullptr;
    hasDiskControlExt_ = false;
    diskControlExtCallback_ = {};

    pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    useHWAcceleration = false;
    useDepth = false;
    useStencil = false;
    bottomLeftOrigin = false;
    screenRotation = 0;
    screenRotationUpdated = false;

    gameGeometryUpdated = false;
    gameGeometryWidth = 0;
    gameGeometryHeight = 0;
    gameGeometryAspectRatio = -1.0f;

    rumbleStates.fill(libretrodroid::RumbleState {});

    hasMemoryMap = false;
    memoryMapGeneration_ = 0;
    memoryMap = {};
    memoryDescriptors.clear();
    memoryAddrspaces.clear();

    hardcoreEffectiveValues_.clear();
    dirtyVariables = true;
}

void Environment::deinitialize() {
    prepareForCoreReload();
    callback_get_current_framebuffer = nullptr;

    savesDirectory = std::string();
    systemDirectory = std::string();
    language = RETRO_LANGUAGE_ENGLISH;

    variables.clear();
    controllers.clear();
    hardcoreActive_ = false;
    hardcoreLibraryName_.clear();
    hardcoreConsoleId_ = 0;
    dirtyVariables = false;
}

void Environment::updateVariable(const std::string& key, const std::string& value) {
    auto current = variables[key];
    current.key = key;

    if (value != current.value) {
        current.value = value;
        variables[key] = current;
        dirtyVariables = true;
    }
}

bool Environment::environment_handle_set_variables(const struct retro_variable* received) {
    unsigned count = 0;
    while (received[count].key != nullptr) {
        LOGD("Received variable %s: %s", received[count].key, received[count].value);

        std::string key(received[count].key);
        std::string description(received[count].value);
        std::string value(received[count].value);

        auto firstValueStart = value.find(';') + 2;
        auto firstValueEnd = value.find('|', firstValueStart);
        value = value.substr(firstValueStart, firstValueEnd - firstValueStart);

        auto currentVariable = variables[key];
        currentVariable.key = key;
        currentVariable.description = description;

        if (currentVariable.value.empty()) {
            currentVariable.value = value;
        }

        variables[key] = currentVariable;
        LOGD("Assigning variable %s: %s", currentVariable.key.c_str(), currentVariable.value.c_str());

        count++;
    }

    return true;
}

void Environment::setHardcoreContext(
    bool active,
    const std::string& libraryName,
    uint32_t consoleId
) {
    bool changed = (hardcoreActive_ != active) ||
                   (hardcoreLibraryName_ != libraryName) ||
                   (hardcoreConsoleId_ != consoleId);
    hardcoreActive_ = active;
    hardcoreLibraryName_ = libraryName;
    hardcoreConsoleId_ = consoleId;
    if (changed) {
        hardcoreEffectiveValues_.clear();
        dirtyVariables = true;
    }
}

bool Environment::isHardcoreActive() const {
    return hardcoreActive_;
}

bool Environment::isSettingAllowedInHardcore(
    const std::string& libraryName,
    uint32_t consoleId,
    const std::string& key,
    const std::string& value
) {
    if (key.empty()) {
        return true;
    }

    if (!libraryName.empty()) {
        const rc_disallowed_setting_t* coreRules =
            rc_libretro_get_disallowed_settings(libraryName.c_str());
        if (coreRules != nullptr &&
            !rc_libretro_is_setting_allowed(coreRules, key.c_str(), value.c_str())) {
            return false;
        }

        if (consoleId != 0) {
            const rc_disallowed_setting_t* sysRules =
                rc_libretro_get_disallowed_settings_for_system(libraryName.c_str(), consoleId);
            if (sysRules != nullptr &&
                !rc_libretro_is_setting_allowed(sysRules, key.c_str(), value.c_str())) {
                return false;
            }
        }
    }

    if (!rc_libretro_is_setting_allowed(
            kArcaduneSupplementDisallowedSettings,
            key.c_str(),
            value.c_str())) {
        return false;
    }

    return true;
}

std::string Environment::resolveHardcoreSafeValue(
    const std::string& libraryName,
    uint32_t consoleId,
    const std::string& key,
    const std::string& currentValue,
    const std::string& description
) {
    if (isSettingAllowedInHardcore(libraryName, consoleId, key, currentValue)) {
        return currentValue;
    }

    for (const auto& candidate : parseOptionCandidates(description)) {
        if (isSettingAllowedInHardcore(libraryName, consoleId, key, candidate)) {
            return candidate;
        }
    }

    for (const auto& fallback : kMandatoryHardcoreDefaults) {
        if (matchesMandatoryKey(fallback.key, key) &&
            isSettingAllowedInHardcore(libraryName, consoleId, key, fallback.safeValue)) {
            return fallback.safeValue;
        }
    }

    static const char* const kGenericSafeCandidates[] = {
        "disabled",
        "enabled",
        "auto",
        "Auto",
        "none",
        "off",
        "false",
        "100%",
        "100"
    };
    for (const char* candidate : kGenericSafeCandidates) {
        if (isSettingAllowedInHardcore(libraryName, consoleId, key, candidate)) {
            return candidate;
        }
    }

    return "disabled";
}

bool Environment::environment_handle_get_variable(struct retro_variable* requested) {
    if (requested == nullptr || requested->key == nullptr) {
        return false;
    }
    std::string key(requested->key);
    LOGD("Variable requested %s", key.c_str());
    auto foundVariable = variables.find(key);

    if (foundVariable == variables.end()) {
        if (hardcoreActive_) {
            for (const auto& mandatory : kMandatoryHardcoreDefaults) {
                if (matchesMandatoryKey(mandatory.key, key)) {
                    auto& stored = hardcoreEffectiveValues_[key];
                    stored = mandatory.safeValue;
                    requested->value = stored.c_str();
                    return true;
                }
            }
        }
        return false;
    }

    if (!hardcoreActive_) {
        requested->value = foundVariable->second.value.c_str();
        return true;
    }

    std::string safeValue = resolveHardcoreSafeValue(
        hardcoreLibraryName_,
        hardcoreConsoleId_,
        key,
        foundVariable->second.value,
        foundVariable->second.description
    );
    auto& stored = hardcoreEffectiveValues_[key];
    stored = std::move(safeValue);
    requested->value = stored.c_str();
    return true;
}

bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
    controllers.clear();

    unsigned player = 0;
    while (received[player].types != nullptr) {

        auto currentPlayer = received[player];

        controllers.emplace_back();

        unsigned controller = 0;
        while (controller < currentPlayer.num_types && currentPlayer.types[controller].desc != nullptr) {
            auto currentController = currentPlayer.types[controller];
            LOGD("Received controller for player %d: %d %s", player, currentController.id, currentController.desc);

            controllers[player].push_back(Controller { currentController.id, currentController.desc });
            controller++;
        }

        player++;
    }

    return true;
}

bool Environment::environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback) {
    useHWAcceleration = true;
    useDepth = hw_render_callback->depth;
    useStencil = hw_render_callback->stencil;
    bottomLeftOrigin = hw_render_callback->bottom_left_origin;

    hw_context_destroy = hw_render_callback->context_destroy;
    hw_context_reset = hw_render_callback->context_reset;
    hw_render_callback->get_current_framebuffer = callback_get_current_framebuffer;
    hw_render_callback->get_proc_address = &eglGetProcAddress;

    return true;
}

bool Environment::environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfsInterfaceInfo) {
    if (!useVirtualFileSystem) {
        return false;
    }

    vfsInterfaceInfo->required_interface_version = libretrodroid::VFS::SUPPORTED_VERSION;
    vfsInterfaceInfo->iface = libretrodroid::VFS::getInterface();
    return true;
}

bool Environment::environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface) {
    if (!enableMicrophone) {
        return false;
    }

    *microphone_interface = *libretrodroid::MicrophoneInterface::getInterface();
    return true;
}

bool Environment::environment_handle_set_memory_maps(const struct retro_memory_map* received) {
    if (received == nullptr) {
        return false;
    }

    memoryDescriptors.clear();
    memoryAddrspaces.clear();

    if (received->descriptors != nullptr && received->num_descriptors > 0) {
        memoryDescriptors.reserve(received->num_descriptors);
        for (unsigned i = 0; i < received->num_descriptors; i++) {
            struct retro_memory_descriptor copy = received->descriptors[i];
            if (copy.addrspace != nullptr) {
                size_t len = strlen(copy.addrspace) + 1;
                auto owned = std::make_unique<char[]>(len);
                memcpy(owned.get(), copy.addrspace, len);
                copy.addrspace = owned.get();
                memoryAddrspaces.push_back(std::move(owned));
            }
            memoryDescriptors.push_back(copy);
        }
    }

    memoryMap.descriptors = memoryDescriptors.empty() ? nullptr : memoryDescriptors.data();
    memoryMap.num_descriptors = static_cast<unsigned>(memoryDescriptors.size());
    hasMemoryMap = true;
    memoryMapGeneration_++;
    return true;
}

void Environment::callback_retro_log(enum retro_log_level level, const char *fmt, ...) {
#if !VERBOSE_LOGGING
    if (level != RETRO_LOG_ERROR) {
        return;
    }
    static std::atomic<int64_t> windowStartMs{0};
    static std::atomic<int> windowCount{0};
    const auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    int64_t startMs = windowStartMs.load(std::memory_order_relaxed);
    if (nowMs - startMs >= 1000) {
        windowStartMs.store(nowMs, std::memory_order_relaxed);
        windowCount.store(0, std::memory_order_relaxed);
    }
    if (windowCount.fetch_add(1, std::memory_order_relaxed) >= 20) {
        return;
    }
#endif

    va_list argptr;
    va_start(argptr, fmt);

    switch (level) {
#if VERBOSE_LOGGING
        case RETRO_LOG_DEBUG:
            __android_log_vprint(ANDROID_LOG_DEBUG, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_INFO:
            __android_log_vprint(ANDROID_LOG_INFO, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_WARN:
            __android_log_vprint(ANDROID_LOG_WARN, MODULE_NAME_CORE, fmt, argptr);
            break;
#endif
        case RETRO_LOG_ERROR:
            __android_log_vprint(ANDROID_LOG_ERROR, MODULE_NAME_CORE, fmt, argptr);
            break;
        default:
            // Log nothing in here.
            break;
    }

    va_end(argptr);
}

bool Environment::callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    return Environment::getInstance().handle_callback_set_rumble_state(port, effect, strength);
}

bool Environment::handle_callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    LOGV("Setting rumble strength for port %i to %i", port, strength);
    if (port < 0 || port > 3) return false;

    if (effect == RETRO_RUMBLE_STRONG) {
        rumbleStates[port].strengthStrong = strength;
    } else if (effect == RETRO_RUMBLE_WEAK) {
        rumbleStates[port].strengthWeak = strength;
    }

    return true;
}

bool Environment::callback_environment(unsigned cmd, void *data) {
    return Environment::getInstance().handle_callback_environment(cmd, data);
}

bool Environment::handle_callback_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *((bool*) data) = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            LOGD("Called SET_PIXEL_FORMAT");
            pixelFormat = *static_cast<enum retro_pixel_format *>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            LOGD("Called SET_INPUT_DESCRIPTORS");
            return false;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE");
            return environment_handle_get_variable(static_cast<struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            LOGD("Called RETRO_ENVIRONMENT_SET_VARIABLES");
            return environment_handle_set_variables(static_cast<const struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE. Is dirty?: %d", dirtyVariables);
            *((bool*) data) = dirtyVariables;
            dirtyVariables = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            LOGD("Called RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER");
            *((unsigned*) data) = retro_hw_context_type::RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_RENDER");
            return environment_handle_set_hw_render(static_cast<struct retro_hw_render_callback*>(data));

        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE");
            ((struct retro_rumble_interface*) data)->set_rumble_state = &callback_set_rumble_state;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LOG_INTERFACE");
            ((struct retro_log_callback*) data)->log = &callback_retro_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY");
            *(const char**) data = savesDirectory.c_str();
            return !savesDirectory.empty();

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY");
            *(const char**) data = systemDirectory.c_str();
            return !systemDirectory.empty();

        case RETRO_ENVIRONMENT_SET_ROTATION: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            unsigned screenRotationIndex = (*static_cast<unsigned*>(data));
            screenRotation = screenRotationIndex * (float) (-M_PI / 2.0);
            screenRotationUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_DISK_CONTROL_INTERFACE_VERSION: {
            if (data != nullptr) {
                *static_cast<unsigned*>(data) = 1u;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE");
            retro_disk_control_callback = static_cast<struct retro_disk_control_callback*>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE");
            if (data == nullptr) {
                hasDiskControlExt_ = false;
                retro_disk_control_callback = nullptr;
                return true;
            }
            diskControlExtCallback_ = *static_cast<const struct retro_disk_control_ext_callback*>(data);
            hasDiskControlExt_ = true;
            retro_disk_control_callback =
                reinterpret_cast<struct retro_disk_control_callback*>(&diskControlExtCallback_);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_PERF_INTERFACE");
            return false;

            // TODO... RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO can also change frame-rate
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            struct retro_game_geometry *geometry = static_cast<struct retro_game_geometry *>(data);
            gameGeometryHeight = geometry->base_height;
            gameGeometryWidth = geometry->base_width;
            gameGeometryAspectRatio = geometry->aspect_ratio;
            gameGeometryUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTROLLER_INFO");
            return environment_handle_set_controller_info(static_cast<const struct retro_controller_info*>(data));

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE");
            return false;

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LANGUAGE");
            *((unsigned*) data) = language;
            return true;

        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VFS_INTERFACE");
            return environment_handle_get_vfs_interface(static_cast<struct retro_vfs_interface_info*>(data));

        case RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE");
            return environment_handle_get_microphone_interface(static_cast<struct retro_microphone_interface*>(data));

        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            LOGD("Called RETRO_ENVIRONMENT_SET_MEMORY_MAPS");
            return environment_handle_set_memory_maps(static_cast<const struct retro_memory_map*>(data));

        default:
            LOGD("callback environment has been called: %u", cmd);
            return false;
    }
}

void Environment::setLanguage(const std::string& androidLanguage) {
    std::unordered_map<std::string, unsigned> languages {
            { "en", RETRO_LANGUAGE_ENGLISH },
            { "jp", RETRO_LANGUAGE_JAPANESE },
            { "fr", RETRO_LANGUAGE_FRENCH },
            { "es", RETRO_LANGUAGE_SPANISH },
            { "de", RETRO_LANGUAGE_GERMAN },
            { "it", RETRO_LANGUAGE_ITALIAN },
            { "nl", RETRO_LANGUAGE_DUTCH },
            { "pt", RETRO_LANGUAGE_PORTUGUESE_PORTUGAL },
            { "ru", RETRO_LANGUAGE_RUSSIAN },
            { "ko", RETRO_LANGUAGE_KOREAN },
            { "zh", RETRO_LANGUAGE_CHINESE_TRADITIONAL },
            { "eo", RETRO_LANGUAGE_ESPERANTO },
            { "pl", RETRO_LANGUAGE_POLISH },
            { "vi", RETRO_LANGUAGE_VIETNAMESE },
            { "ar", RETRO_LANGUAGE_ARABIC },
            { "el", RETRO_LANGUAGE_GREEK },
            { "tr", RETRO_LANGUAGE_TURKISH }
    };

    if (languages.find(androidLanguage) != languages.end()) {
        language = languages[androidLanguage];
    }
}

retro_hw_context_reset_t Environment::getHwContextReset() const {
    return hw_context_reset;
}

retro_hw_context_reset_t Environment::getHwContextDestroy() const {
    return hw_context_destroy;
}

struct retro_disk_control_callback* Environment::getRetroDiskControlCallback() const {
    return retro_disk_control_callback;
}

std::string Environment::getDiskImagePath(unsigned index) const {
    if (hasDiskControlExt_ && diskControlExtCallback_.get_image_path != nullptr) {
        char pathBuf[4096] = {};
        if (diskControlExtCallback_.get_image_path(index, pathBuf, sizeof(pathBuf)) &&
            pathBuf[0] != '\0') {
            return std::string(pathBuf);
        }
    }
    return {};
}

int Environment::getPixelFormat() const {
    return pixelFormat;
}

bool Environment::isUseHwAcceleration() const {
    return useHWAcceleration;
}

bool Environment::isUseDepth() const {
    return useDepth;
}

bool Environment::isUseStencil() const {
    return useStencil;
}

bool Environment::isBottomLeftOrigin() const {
    return bottomLeftOrigin;
}

float Environment::getScreenRotation() const {
    return screenRotation;
}

bool Environment::isGameGeometryUpdated() const {
    return gameGeometryUpdated;
}

void Environment::clearGameGeometryUpdated() {
    gameGeometryUpdated = false;
}

unsigned int Environment::getGameGeometryWidth() const {
    return gameGeometryWidth;
}

unsigned int Environment::getGameGeometryHeight() const {
    return gameGeometryHeight;
}

float Environment::getGameGeometryAspectRatio() const {
    return gameGeometryAspectRatio;
}

const std::vector<struct Variable> Environment::getVariables() const {
    std::vector<struct Variable> result;

    std::for_each(
        variables.begin(),
        variables.end(),
        [&](std::pair<std::string, struct Variable> item) {
            result.push_back(item.second);
        }
    );

    std::sort(
        result.begin(),
        result.end(),
        [](struct Variable v1, struct Variable v2) {
            return v1.key < v2.key;
        }
    );

    return result;
}

const std::vector<std::vector<struct Controller>> &Environment::getControllers() const {
    return controllers;
}

const struct retro_memory_map* Environment::getMemoryMap() const {
    return hasMemoryMap ? &memoryMap : nullptr;
}

uint64_t Environment::getMemoryMapGeneration() const {
    return memoryMapGeneration_;
}

float Environment::retrieveGameSpecificAspectRatio() {
    if (getGameGeometryAspectRatio() > 0) {
        return getGameGeometryAspectRatio();
    }

    if (getGameGeometryWidth() > 0 && getGameGeometryHeight() > 0) {
        return (float) getGameGeometryWidth() / (float) getGameGeometryHeight();
    }

    return -1.0f;
}

bool Environment::isScreenRotationUpdated() const {
    return screenRotationUpdated;
}

void Environment::clearScreenRotationUpdated() {
    screenRotationUpdated = false;
}

std::array<libretrodroid::RumbleState, 4>& Environment::getLastRumbleStates() {
    return rumbleStates;
}

void Environment::setEnableVirtualFileSystem(bool value) {
    this->useVirtualFileSystem = value;
}

void Environment::setEnableMicrophone(bool value) {
    this->enableMicrophone = value;
}
