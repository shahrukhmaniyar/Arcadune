package com.swordfish.lemuroid.app.shared.coreoptions

import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.libretrodroid.Variable
import java.io.Serializable

data class CoreOption(
    val variable: CoreVariable,
    val name: String,
    val optionValues: List<String>,
) : Serializable {
    companion object {
        fun fromLibretroDroidVariable(variable: Variable): CoreOption {
            val parts = variable.description?.split(";", limit = 2).orEmpty()
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val values = parts.getOrNull(1)?.trim()?.split('|')?.filter { it.isNotEmpty() } ?: emptyList()
            val coreVariable = CoreVariable(variable.key.orEmpty(), variable.value.orEmpty())
            return CoreOption(coreVariable, name, values)
        }
    }
}
