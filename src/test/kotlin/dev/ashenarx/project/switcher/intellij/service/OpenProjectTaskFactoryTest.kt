package dev.ashenarx.project.switcher.intellij.service

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenProjectTaskFactoryTest {

    @Test
    fun `factory configures project opening options`() {
        val newFrame = OpenProjectTaskFactory.create(null, true, false)
        val reusedFrame = OpenProjectTaskFactory.create(null, false, true)

        assertTrue(newFrame.forceOpenInNewFrame)
        assertFalse(newFrame.forceReuseFrame)
        assertTrue(newFrame.runConfigurators)
        assertFalse(reusedFrame.forceOpenInNewFrame)
        assertTrue(reusedFrame.forceReuseFrame)
        assertTrue(reusedFrame.runConfigurators)
    }

    @Test
    fun `factory bytecode stays behind the builder boundary`() {
        val resource = OpenProjectTaskFactory::class.java.name.replace('.', '/') + ".class"
        val platformCalls = methodCalls(resource)
            .filter { it.startsWith("com/intellij/ide/impl/OpenProjectTask") }

        assertTrue(platformCalls.any { it.startsWith("com/intellij/ide/impl/OpenProjectTaskBuilder.build(") })
        assertEquals(
            emptyList<String>(),
            platformCalls.filter {
                it.startsWith("com/intellij/ide/impl/OpenProjectTask.<init>") ||
                    it.startsWith("com/intellij/ide/impl/OpenProjectTask.copy")
            },
            "construction must not depend on OpenProjectTask's data-class ABI",
        )
        assertEquals(
            emptyList<String>(),
            platformCalls.filter { it.startsWith("com/intellij/ide/impl/OpenProjectTaskBuilder.setRunConfigurators(") },
            "runConfigurators changed from Boolean to Boolean? in 263, so its setter must be bound at runtime",
        )
    }

    @Test
    fun `project opener routes task construction through the factory`() {
        val resource = ProjectOpener::class.java.name.replace('.', '/') + "\$reopen\$1.class"
        val calls = methodCalls(resource)

        assertTrue(
            calls.any { it.startsWith("dev/ashenarx/project/switcher/intellij/service/OpenProjectTaskFactory.create(") },
            "ProjectOpener.reopen must use the non-inline factory boundary",
        )
        assertEquals(
            emptyList<String>(),
            calls.filter { it.startsWith("com/intellij/ide/impl/OpenProjectTask.copy") },
        )
    }

    private fun methodCalls(resource: String): List<String> {
        val calls = mutableListOf<String>()
        val bytes = checkNotNull(OpenProjectTaskFactory::class.java.classLoader.getResourceAsStream(resource)) {
            "cannot read $resource"
        }.use { it.readAllBytes() }
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String?>?,
            ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean,
                ) {
                    calls += "$owner.$name$descriptor"
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return calls
    }
}
