package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_ANALOG_LEFT
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_ANALOG_RIGHT
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_CROSS
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_DISABLED
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L1_R1
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L2_R2
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L_R
import com.swordfish.touchinput.radial.settings.TouchControllerID

// TODO PADS... Make sure the ids are correct.
object ControllerConfigs {
    val ATARI_2600 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI2600,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val NES =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NES,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val SNES =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.SNES,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val SMS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.SMS,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GENESIS_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_genesis_6,
            TouchControllerID.GENESIS_6,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "MD Joypad 6 Button",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GENESIS_3 =
        ControllerConfig(
            "default_3",
            R.string.controller_genesis_3,
            TouchControllerID.GENESIS_3,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "MD Joypad 3 Button",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val BLASTEM_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_genesis_6,
            TouchControllerID.GENESIS_6,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "6-button gamepad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val BLASTEM_3 =
        ControllerConfig(
            "default_3",
            R.string.controller_genesis_3,
            TouchControllerID.GENESIS_3,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "3-button gamepad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GG =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GG,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GB =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GB,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GBA =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GBA,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val N64 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.N64,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val PSX_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.PSX,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "standard",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val PSX_DUALSHOCK =
        ControllerConfig(
            "dualshock",
            R.string.controller_dualshock,
            TouchControllerID.PSX_DUALSHOCK,
            allowTouchRotation = true,
            libretroDescriptor = "dualshock",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val SWANSTATION_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.PSX,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "Digital Controller (Gamepad)",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val SWANSTATION_DUALSHOCK =
        ControllerConfig(
            "dualshock",
            R.string.controller_dualshock,
            TouchControllerID.PSX_DUALSHOCK,
            allowTouchRotation = true,
            libretroDescriptor = "Analog Controller (DualShock)",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val BEETLE_PSX_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.PSX,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "PlayStation Controller",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val BEETLE_PSX_DUALSHOCK =
        ControllerConfig(
            "dualshock",
            R.string.controller_dualshock,
            TouchControllerID.PSX_DUALSHOCK,
            allowTouchRotation = true,
            libretroDescriptor = "DualShock",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val PSP =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.PSP,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val FB_NEO_4 =
        ControllerConfig(
            "default_4",
            R.string.controller_arcade_4,
            TouchControllerID.ARCADE_4,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val FB_NEO_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_arcade_6,
            TouchControllerID.ARCADE_6,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MAME_2003_4 =
        ControllerConfig(
            "default_4",
            R.string.controller_arcade_4,
            TouchControllerID.ARCADE_4,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MAME_2003_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_arcade_6,
            TouchControllerID.ARCADE_6,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MELONDS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.MELONDS,
            mergeDPADAndLeftStickEvents = true,
            allowTouchOverlay = false,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val LYNX =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.LYNX,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val ATARI7800 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI7800,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val PCE =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.PCE,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val NGP =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NGP,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val DOS_AUTO =
        ControllerConfig(
            "auto",
            R.string.controller_dos_auto,
            TouchControllerID.DOS,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val WS_LANDSCAPE =
        ControllerConfig(
            "landscape",
            R.string.controller_landscape,
            TouchControllerID.WS_LANDSCAPE,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val WS_PORTRAIT =
        ControllerConfig(
            "portrait",
            R.string.controller_portrait,
            TouchControllerID.WS_PORTRAIT,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val NINTENDO_3DS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NINTENDO_3DS,
            allowTouchOverlay = false,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val DREAMCAST =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.DREAMCAST,
            allowTouchRotation = true,
            libretroDescriptor = "Dreamcast Controller",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val THREEDO =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.THREEDO,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "3DO Joypad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val SATURN_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.SATURN,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "Saturn Pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val SATURN_3D =
        ControllerConfig(
            "3d_pad",
            R.string.controller_3d_pad,
            TouchControllerID.SATURN_3D,
            allowTouchRotation = true,
            libretroDescriptor = "Saturn 3D Pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val BEETLE_SATURN_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.SATURN,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "Control Pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val BEETLE_SATURN_3D =
        ControllerConfig(
            "3d_pad",
            R.string.controller_3d_pad,
            TouchControllerID.SATURN_3D,
            allowTouchRotation = true,
            libretroDescriptor = "3D Control Pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val PICODRIVE_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_genesis_6,
            TouchControllerID.GENESIS_6,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "6 button pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val PICODRIVE_3 =
        ControllerConfig(
            "default_3",
            R.string.controller_genesis_3,
            TouchControllerID.GENESIS_3,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "3 button pad",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val ATARI5200 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI5200,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val ATARI800 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI800,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val COLECOVISION =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.COLECOVISION,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val C64 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.C64,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val APPLEII =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.APPLEII,
            allowTouchRotation = true,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "Analog Joypad",
            libretroId = 5,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MSX =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.MSX,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )
}
