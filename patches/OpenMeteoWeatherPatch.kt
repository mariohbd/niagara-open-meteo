package app.d0nj.patches.niagara

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Deliberately fail closed: obfuscated member contracts are specific to the supported builds. */
@Suppress("unused")
val openMeteoWeatherPatch = bytecodePatch(
    name = "Open-Meteo weather",
    description = "Use Open-Meteo for current, hourly and daily weather in the original widget. " +
        "Sends the configured weather coordinates to api.open-meteo.com. No minute-by-minute rain alerts. " +
        "Personal, non-commercial use; data CC BY 4.0. Experimental: Niagara 1.16.28 (1634) and 1.16.29 (1635).",
    default = false,
) {
    compatibleWith(Compatibility(
        packageName = "bitpit.launcher",
        name = "Niagara Launcher",
        targets = listOf(AppTarget(version = "1.16.28"), AppTarget(version = "1.16.29")),
    ))
    extendWith("extensions/open-meteo.mpe")
    execute {
        val is1635 = packageMetadata.versionCode == "1635" && packageMetadata.versionName == "1.16.29"
        if (!is1635 && (packageMetadata.versionCode != "1634" || packageMetadata.versionName != "1.16.28"))
            throw PatchException("Open-Meteo requires Niagara 1.16.28 build 1634 or 1.16.29 build 1635")

        fun field(type: String, name: String, descriptor: String) {
            if (classDefBy(type).fields.none { it.name == name && it.type == descriptor })
                throw PatchException("Unexpected weather field: $type->$name")
        }
        fun method(type: String, name: String, parameters: List<String>, result: String) {
            if (classDefBy(type).methods.none {
                    it.name == name && it.parameterTypes.map { p -> p.toString() } == parameters && it.returnType == result
                }) throw PatchException("Unexpected weather method: $type->$name")
        }
        val coroutine = if (is1635) "Lb/Hu7yEGV1H1r1bdCCcwuur8scnv1v;" else "Lb/hKoAGpeDNCeUKj;"
        val discriminator = if (is1635) "SB71Xg0Ef2yAewcpeisEWC" else "iFfaGW9rDrCFkMXIphvn"
        val repository = if (is1635) "Lb/xC7VAkwDePsNJ396LU;" else "Lb/Hwu2qIgzKhvPNXQPk7;"
        val coordinates = if (is1635) "Lb/yOT9KFkE4RgdBmVfo9k4ZYgHH;" else "Lb/TF17y8cNctwJlmOtJXE;"
        field(coroutine, discriminator, "I")
        field(coroutine, if (is1635) "ojn5c2YOWTFy0045br2ifslIhtlq" else "Xg3202PBvhqN2gTjXPAuDAPu7", "I")
        (if (is1635) listOf("ECePjANygrkOgQnMq53", "Qph1ZWcbx3tkLvD", "TfTzbajyaQF1")
        else listOf("M9Fe8pvPjYM", "mSzisdZo1gIkaJJamui2eM", "JfD5anH8HmnM40u")).forEach {
            field(coroutine, it, "Ljava/lang/Object;")
        }
        field(coordinates, if (is1635) "Kj2k1AqaZsaVCT" else "scSVXSelHn0vGdEhMIkeOConMr", "F")
        field(coordinates, if (is1635) "SJiFQ7HDN1InfkB1Erx8z20l" else "lJFNcBQHCClkpT8l7Xkn8v", "F")
        method(repository, if (is1635) "gE0rVOWsj4loTBBoy2xSrtVpMP1H" else "RABvSkqAo10GtuvvLvFAVN", listOf("Ljava/lang/String;", "J"), if (is1635) "Lb/pmLuZa5P6AoCZIpAX;" else "Lb/WYklSmEg37aP;")
        method(repository, if (is1635) "ZFqutaGTYq" else "PIFjKdTFx4bkGdxkn5oksjktQ8eg", listOf("Ljava/lang/String;"), "V")
        method(if (is1635) "Lb/Qx3wzSHU2H1QDj;" else "Lb/DCcE1vyHs4o;", if (is1635) "SJiFQ7HDN1InfkB1Erx8z20l" else "aD3Ncu302iGQ5j7cZ7nBmv2dsVG", listOf("Ljava/lang/Object;"), "V")
        method("Lbitpit/launcher/weather/NoWeatherDataException;", "<init>",
            listOf("I", "Ljava/lang/Exception;", "Ljava/lang/String;"), "V")

        val target = mutableClassDefBy(coroutine).methods.single {
            it.name == (if (is1635) "A1Md0KwGu5VUxb7FHaW" else "QU0xcQ3PL93h1HkpS1") && it.parameterTypes == listOf("Ljava/lang/Object;")
        }
        val implementation = target.implementation ?: throw PatchException("Missing coroutine implementation")
        val strings = implementation.instructions.mapNotNull {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
        }
        if (!strings.containsAll(listOf("weather2", "lat", "long", "lang", "st missing"))
            || implementation.registerCount < 5) throw PatchException("Weather coroutine fingerprint mismatch")
        if (implementation.instructions.any {
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass ==
                    "Lapp/d0nj/extension/weather/NiagaraWeatherBridge;"
            }) throw PatchException("Already patched")
        // This R8-merged coroutine contains 14 branches. Intercept only weather branch 13.
        // v0-v2 are scratch at entry; p0/p1 and the other branches remain intact.
        target.addInstructionsWithLabels(0, """
            move-object/from16 v0, p0
            iget v1, v0, $coroutine->$discriminator:I
            const/16 v2, 0xd
            if-ne v1, v2, :original
            invoke-static/range {p0 .. p1}, Lapp/d0nj/extension/weather/NiagaraWeatherBridge;->fetch(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
            return-object v0
        """.trimIndent(), ExternalLabel("original", implementation.instructions.first()))

        val about = mutableClassDefBy(if (is1635) "Lb/V90i4gEMcGLCcnbrkYt;" else "Lb/vrhe4FRDCvomUW;").methods.single {
            it.name == (if (is1635) "Kj2k1AqaZsaVCT" else "scSVXSelHn0vGdEhMIkeOConMr")
        }
        val instructions = about.implementation!!.instructions.toList()
        val sourceIndex = instructions.indices.single { index ->
            (instructions[index] as? NarrowLiteralInstruction)?.narrowLiteral == 0x7f12056f
        }
        val call = instructions[sourceIndex + 1] as? ReferenceInstruction
        val ref = call?.reference as? MethodReference
        val result = instructions[sourceIndex + 2]
        if (ref?.definingClass != "Landroid/content/Context;" || ref.name != "getString"
            || result.opcode != Opcode.MOVE_RESULT_OBJECT) throw PatchException("Attribution fingerprint mismatch")
        val register = (result as OneRegisterInstruction).registerA
        // Keep the resource call valid; overwrite its result only for the provider attribution.
        about.addInstructionsWithLabels(sourceIndex + 3,
            "const-string v$register, \"Open-Meteo (CC BY 4.0)\"")
        val link = about.implementation!!.instructions.withIndex().single {
            ((it.value as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                "https://help.niagaralauncher.app/article/105-weather-widget"
        }
        val linkRegister = (link.value as OneRegisterInstruction).registerA
        about.replaceInstruction(link.index, "const-string v$linkRegister, \"https://open-meteo.com/\"")
    }
}

