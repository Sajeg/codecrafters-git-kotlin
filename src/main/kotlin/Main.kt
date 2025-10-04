import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.err.println("Logs from your program will appear here!")

    if (args.isEmpty()) {
        println("Usage: your_program.sh <command> [<args>]")
        exitProcess(1)
    }
    when (args[0]) {
        "init" -> {
            val gitDir = File(".git")
            gitDir.mkdir()
            File(gitDir, "objects").mkdir()
            File(gitDir, "refs").mkdir()
            File(gitDir, "HEAD").writeText("ref: refs/heads/master\n")

            println("Initialized git directory")
        }

        "cat-file" -> {
            if (args[1] == "-p") {
                val inputStream =
                    File(".git/objects/${args[2].subSequence(0, 2)}/${args[2].subSequence(2, 40)}").inputStream()
                val blob = inputStream.readAllBytes().zlibDecompress()
                print(blob.split('\u0000')[1])
            }
        }

        "hash-object" -> {
            var writeFile = false
            val path = if (args[1] == "-w") {
                writeFile = true
                args[2]
            } else {
                args[1]
            }
            File(path).inputStream().use { fileInputStream ->
                val fileContent = fileInputStream.readAllBytes()
                val hexChars = "0123456789abcdef"
                val bytes = MessageDigest
                    .getInstance("SHA-1")
                    .digest(fileContent)
                val hash = StringBuilder(bytes.size * 2)

                bytes.forEach {
                    val i = it.toInt()
                    hash.append(hexChars[i shr 4 and 0x0f])
                    hash.append(hexChars[i and 0x0f])
                }
                println(hash)
                if (!writeFile) {
                    return
                }
                val blob = "blob ${fileContent.size}\u0000".toByteArray(Charsets.UTF_8)
                val compressedBlob = blob.plus(fileContent).zlibCompress()
                File(".git/objects/${hash.subSequence(0, 2)}/").mkdirs()
                File(".git/objects/${hash.subSequence(0, 2)}/${hash.subSequence(2, 40)}").apply {
                    createNewFile()
                    writeBytes(compressedBlob)
                }
            }
        }

        else -> {
            println("Unknown command: ${args[0]}")
            exitProcess(1)
        }
    }

}

fun ByteArray.zlibCompress(): ByteArray {
    val output = ByteArray(this@zlibCompress.size * 4)
    val compressor = Deflater().apply {
        setInput(this@zlibCompress)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    return output.copyOfRange(0, compressedDataLength)
}


fun ByteArray.zlibDecompress(): String {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toString("UTF-8")
    }
}
