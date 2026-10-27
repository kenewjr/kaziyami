package app.kaziyami.bytecode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

class BytecodeVersionContractTest {

    @Test
    fun testClassFileMajorVersionWithinSupportedBounds() {
        val rootDir = listOf(File(".."), File(".")).firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")

        // 1. Inspect compiled classes in build/classes/kotlin/main (DEX inputs)
        val mainClassesDir = File(rootDir, "core/build/classes/kotlin/main")
        assertTrue(mainClassesDir.exists(), "Core compiled classes directory must exist")

        val diskClasses = mainClassesDir.walkTopDown().filter { it.extension == "class" }.toList()
        assertTrue(diskClasses.isNotEmpty(), "Must find compiled program classes in core")

        for (clsFile in diskClasses) {
            DataInputStream(FileInputStream(clsFile)).use { dis ->
                val magic = dis.readInt()
                assertEquals(
                    0xCAFEBABE.toInt(),
                    magic,
                    "Class ${clsFile.name} must start with standard JVM magic 0xCAFEBABE"
                )
                dis.skipBytes(2)
                val major = dis.readUnsignedShort()
                assertTrue(
                    major in 55..61,
                    "Class ${clsFile.name} has major version $major, outside allowed JDK 11..17 range (55..61)"
                )
            }
        }

        // 2. If suite JAR exists in build/libs or repo/jar, also inspect its classes
        val jarDirs = listOf(File(rootDir, "build/libs"), File(rootDir, "repo/jar"))
        val jarFile = jarDirs.firstNotNullOfOrNull { dir ->
            if (dir.exists()) {
                dir.listFiles { f -> f.name.startsWith("kaziyami-all-v") && f.name.endsWith(".jar") }?.firstOrNull()
            } else null
        }

        if (jarFile != null && jarFile.exists()) {
            ZipFile(jarFile).use { zip ->
                val classEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .toList()

                for (entry in classEntries) {
                    DataInputStream(zip.getInputStream(entry)).use { dis ->
                        val magic = dis.readInt()
                        assertEquals(
                            0xCAFEBABE.toInt(),
                            magic,
                            "JAR class ${entry.name} must start with standard JVM magic 0xCAFEBABE"
                        )
                        dis.skipBytes(2)
                        val major = dis.readUnsignedShort()
                        assertTrue(
                            major in 55..61,
                            "JAR class ${entry.name} has major version $major, outside allowed JDK 11..17 range (55..61)"
                        )
                    }
                }
            }
        }
    }
}
