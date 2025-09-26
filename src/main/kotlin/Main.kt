import java.io.ByteArrayOutputStream
import java.io.File
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
                val inputStream = File(".git/objects/${args[2].subSequence(0, 2)}/${args[2].subSequence(2, 40)}").inputStream()
                val blob = inputStream.readAllBytes().zlibDecompress()
                print(blob.split('\u0000')[1])
            }
        }
        else -> {
            println("Unknown command: ${args[0]}")
            exitProcess(1)
        }
    }
         
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
