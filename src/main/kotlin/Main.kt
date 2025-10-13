import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.io.path.Path
import kotlin.io.path.isSymbolicLink
import kotlin.system.exitProcess

class TreeObjects(
    val permission: String,
    val name: String,
    val hash: String
)

const val hexChars = "0123456789abcdef"
const val folderPrefix = ".git"

@OptIn(ExperimentalStdlibApi::class)
fun main(args: Array<String>) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.err.println("Logs from your program will appear here!")

    if (args.isEmpty()) {
        println("Usage: your_program.sh <command> [<args>]")
        exitProcess(1)
    }
    when (args[0]) {
        "init" -> {
            val gitDir = File(folderPrefix)
            gitDir.mkdir()
            File(gitDir, "objects").mkdir()
            File(gitDir, "refs").mkdir()
            File(gitDir, "HEAD").writeText("ref: refs/heads/master\n")

            println("Initialized git directory")
        }

        "cat-file" -> {
            if (args[1] == "-p") {
                val inputStream =
                    File("${folderPrefix}/objects/${args[2].subSequence(0, 2)}/${args[2].subSequence(2, 40)}").inputStream()
                val blob = inputStream.readAllBytes().zlibDecompress().decodeToString()
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
            println(createBlob(writeFile, path))
        }

        "ls-tree" -> {
            var nameOnly = false
            val path = if (args[1] == "--name-only") {
                nameOnly = true
                args[2]
            } else {
                args[1]
            }

            File("${folderPrefix}/objects/${path.subSequence(0, 2)}/${path.subSequence(2, 40)}").inputStream()
                .use { fileInputStream ->
                    val fileContent = fileInputStream.readAllBytes().zlibDecompress()
                    val treeObjects = mutableListOf<TreeObjects>()
                    var startPosition = fileContent.indexOf(0) + 1
                    while (startPosition < fileContent.size) {
                        val zeroPosition =
                            fileContent.copyOfRange(startPosition, fileContent.size).indexOf(0.toByte()) + startPosition
                        val splitString =
                            fileContent.copyOfRange(startPosition, zeroPosition).decodeToString().trim().split(' ')
                        treeObjects.add(
                            TreeObjects(
                                splitString[0],
                                splitString[1],
                                fileContent.copyOfRange(zeroPosition, zeroPosition + 20).toHexString()
                            )
                        )
                        startPosition = zeroPosition + 21
                    }

                    if (nameOnly) {
                        treeObjects.sortedBy { it.name.trim().lowercase() }.forEach {
                            println(it.name)
                        }
                    } else {
                        treeObjects.sortedBy { it.name.trim().lowercase() }.forEach {
                            println("${if (it.permission == "40000") "040000" else it.permission} ${if (it.permission == "40000") "tree" else "blob"} ${it.hash}\t${it.name}")
                        }
                    }
                }
        }
        
        "write-tree" -> {
            val path = Paths.get("").toAbsolutePath().toString()
            println(createTree(File(path)))
        }

        else -> {
            println("Unknown command: ${args[0]}")
            exitProcess(1)
        }
    }

}

@OptIn(ExperimentalStdlibApi::class)
fun createTree(path: File): String {
    val files = path.listFiles()
    val treeObjects = mutableListOf<TreeObjects>()
    files?.forEach loop@ { file ->
        if (file.name == folderPrefix) {
            return@loop
        }
        if (file.isDirectory) {
            treeObjects.add(TreeObjects("40000", file.name, createTree(file)))
        } else if (file.canExecute()) {
            treeObjects.add(TreeObjects("100755", file.name, createBlob(true, file.path)))
        } else if (Path(file.path).isSymbolicLink()) {
            treeObjects.add(TreeObjects("120000", file.name, createBlob(true, file.path)))
        } else {
            treeObjects.add(TreeObjects("100644", file.name, createBlob(true, file.path))) 
        }
    }
    val treeContent = mutableListOf<String>()
    treeObjects.forEach { tree ->
        treeContent.add("${tree.permission} ${tree.name}\u0000${tree.hash.toByteArray().toHexString()}")
    }
    val fileContent = treeContent.joinToString("")
    val tree = "tree ${fileContent.length}\u0000".toByteArray(Charsets.UTF_8)
    val bytes = MessageDigest
        .getInstance("SHA-1")
        .digest(tree.plus(fileContent.toByteArray()))
    val hash = StringBuilder(bytes.size * 2)

    bytes.forEach {
        val i = it.toInt()
        hash.append(hexChars[i shr 4 and 0x0f])
        hash.append(hexChars[i and 0x0f])
    }

    val compressedTree = tree.plus(fileContent.toByteArray()).zlibCompress()
    File("${folderPrefix}/objects/${hash.subSequence(0, 2)}/").mkdirs()
    File("${folderPrefix}/objects/${hash.subSequence(0, 2)}/${hash.subSequence(2, 40)}").apply {
        createNewFile()
        writeBytes(compressedTree)
    }
    return hash.toString()
}

fun createBlob(writeFile: Boolean, path: String): String {
    File(path).inputStream().use { fileInputStream ->
        val fileContent = fileInputStream.readAllBytes()
        val blob = "blob ${fileContent.size}\u0000".toByteArray(Charsets.UTF_8)
        val bytes = MessageDigest
            .getInstance("SHA-1")
            .digest(blob.plus(fileContent))
        val hash = StringBuilder(bytes.size * 2)

        bytes.forEach {
            val i = it.toInt()
            hash.append(hexChars[i shr 4 and 0x0f])
            hash.append(hexChars[i and 0x0f])
        }
        if (!writeFile) {
            return hash.toString()
        }

        val compressedBlob = blob.plus(fileContent).zlibCompress()
        File("${folderPrefix}/objects/${hash.subSequence(0, 2)}/").mkdirs()
        File("${folderPrefix}/objects/${hash.subSequence(0, 2)}/${hash.subSequence(2, 40)}").apply {
            createNewFile()
            writeBytes(compressedBlob)
        }
        return hash.toString()
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


fun ByteArray.zlibDecompress(): ByteArray {
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
        outputStream.toByteArray()
    }
}
