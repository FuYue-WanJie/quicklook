package wanjie.quicklook.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Date
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream

/** 归档需要密码 */
class ArchivePasswordRequiredException(message: String = "Password required") : Exception(message)

/** 归档密码错误 */
class WrongArchivePasswordException(message: String = "Wrong password") : Exception(message)

/**
 * 压缩包解析仓库：基于 SevenZipJBinding，支持 zip/7z/rar/tar/gz 等多格式。
 * 读取条目、判断加密、虚拟路径导航、提取单个条目。
 * 所有方法应在 IO 线程调用。
 */
object ArchiveRepository {

    private const val TAG = "ArchiveRepository"

    /**
     * 快速判断文件是否为加密归档。
     * 尝试打开归档，如果触发密码回调则返回 true。
     */
    fun isEncrypted(filePath: String): Boolean = runCatching {
        val raf = RandomAccessFile(filePath, "r")
        var archive: IInArchive? = null
        try {
            val callback = OpenCallback(null)
            archive = try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), callback)
            } catch (e: SevenZipException) {
                if (callback.passwordRequested) return true
                throw e
            }
            false
        } catch (e: ArchivePasswordRequiredException) {
            true
        } catch (e: Exception) {
            Log.e(TAG, "isEncrypted check failed", e)
            false
        } finally {
            try { archive?.close() } catch (_: Exception) {}
            try { raf.close() } catch (_: Exception) {}
        }
    }.getOrDefault(false)

    /**
     * 读取压缩包全部条目。
     * @param password 密码，非加密归档传 null
     * @throws ArchivePasswordRequiredException 需要密码但未提供
     * @throws WrongArchivePasswordException 密码错误
     * @throws Exception 文件损坏或格式不支持
     */
    fun readAllEntries(filePath: String, password: String?): List<ArchiveEntry> {
        val raf = RandomAccessFile(filePath, "r")
        var archive: IInArchive? = null
        try {
            val callback = OpenCallback(password)
            archive = try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), callback)
            } catch (e: SevenZipException) {
                if (callback.passwordRequested) throw ArchivePasswordRequiredException()
                throw e
            }
            val count = archive.numberOfItems
            val result = ArrayList<ArchiveEntry>(count)
            for (i in 0 until count) {
                val rawPath = (archive.getProperty(i, PropID.PATH) as? String) ?: continue
                val path = rawPath.replace('\\', '/').trimEnd('/')
                if (path.isEmpty()) continue
                val isDir = (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean) ?: false
                val encrypted = (archive.getProperty(i, PropID.ENCRYPTED) as? Boolean) ?: false
                val size = (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                val packedSize = (archive.getProperty(i, PropID.PACKED_SIZE) as? Long) ?: 0L
                val mtime = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L
                val name = path.substringAfterLast('/')
                result.add(
                    ArchiveEntry(
                        name = name,
                        path = if (isDir) "$path/" else path,
                        isDirectory = isDir,
                        size = size,
                        compressedSize = packedSize,
                        lastModified = mtime,
                        encrypted = encrypted && !isDir,
                    )
                )
            }
            return result
        } finally {
            try { archive?.close() } catch (_: Exception) {}
            try { raf.close() } catch (_: Exception) {}
        }
    }

    /**
     * 从全部条目中提取某个虚拟路径下的直接子项。
     * 兼容归档中不存在独立目录条目的情况（从文件路径推断隐式目录）。
     *
     * @param entries 全部条目
     * @param innerPath 当前虚拟路径，根目录为 ""，子目录如 "folder/sub/"
     * @return 直接子项列表（目录优先）
     */
    fun listInPath(entries: List<ArchiveEntry>, innerPath: String): List<ArchiveEntry> {
        val prefix = innerPath
        val children = LinkedHashMap<String, ArchiveEntry>()

        for (entry in entries) {
            val p = entry.path
            if (!p.startsWith(prefix)) continue
            val remaining = p.removePrefix(prefix)
            if (remaining.isEmpty()) continue

            val slashIdx = remaining.indexOf('/')
            if (slashIdx < 0) {
                // 直接子文件
                children[p] = entry
            } else {
                // 子目录：取第一段作为目录名
                val dirName = remaining.substring(0, slashIdx)
                if (dirName.isEmpty()) continue
                val dirPath = prefix + dirName + "/"
                // 只在尚未添加时创建隐式目录（保留真实目录条目的元数据）
                if (dirPath !in children) {
                    children[dirPath] = ArchiveEntry(
                        name = dirName,
                        path = dirPath,
                        isDirectory = true,
                        size = 0L,
                        compressedSize = 0L,
                        lastModified = 0L,
                    )
                } else if (entry.isDirectory && entry.path == dirPath) {
                    // 如果归档自带目录条目，用它替换隐式创建的
                    children[dirPath] = entry
                }
            }
        }
        // 目录优先，再按名称排序
        return children.values.sortedWith(
            compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.name }
        )
    }

    /** 构建面包屑：压缩包名 → 各级目录。 */
    fun buildCrumbs(fileName: String, innerPath: String): List<ArchiveCrumb> {
        val crumbs = mutableListOf(ArchiveCrumb(fileName, ""))
        if (innerPath.isEmpty()) return crumbs
        val parts = innerPath.trimEnd('/').split('/')
        var acc = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            acc += "$part/"
            crumbs.add(ArchiveCrumb(part, acc))
        }
        return crumbs
    }

    /**
     * 提取单个文件条目到指定目录，返回提取后的 File。
     * 调用方负责清理临时文件。
     *
     * @throws ArchivePasswordRequiredException 需要密码但未提供
     * @throws WrongArchivePasswordException 密码错误
     */
    fun extractEntry(filePath: String, password: String?, entryPath: String, destDir: File): File? {
        val raf = RandomAccessFile(filePath, "r")
        var archive: IInArchive? = null
        var out: FileOutputStream? = null
        try {
            val callback = OpenCallback(password)
            archive = try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), callback)
            } catch (e: SevenZipException) {
                if (callback.passwordRequested) throw ArchivePasswordRequiredException()
                throw e
            }
            val target = entryPath.replace('\\', '/').trimEnd('/')
            val count = archive.numberOfItems
            var index = -1
            for (i in 0 until count) {
                val p = (archive.getProperty(i, PropID.PATH) as? String ?: "")
                    .replace('\\', '/').trimEnd('/')
                if (p == target) {
                    index = i
                    break
                }
            }
            if (index < 0) return null

            destDir.mkdirs()
            val destFile = File(destDir, target.substringAfterLast('/'))
            out = FileOutputStream(destFile)
            val outStream = object : ISequentialOutStream {
                override fun write(data: ByteArray?): Int {
                    if (data != null && data.isNotEmpty()) out.write(data)
                    return data?.size ?: 0
                }
            }
            val result = archive.extractSlow(index, outStream, password ?: "")
            return when (result) {
                ExtractOperationResult.OK -> destFile
                ExtractOperationResult.WRONG_PASSWORD -> throw WrongArchivePasswordException()
                else -> null
            }
        } catch (e: ArchivePasswordRequiredException) {
            throw e
        } catch (e: WrongArchivePasswordException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "extractEntry failed", e)
            return null
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { archive?.close() } catch (_: Exception) {}
            try { raf.close() } catch (_: Exception) {}
        }
    }

    /**
     * 打开归档回调，处理密码请求。
     * 当归档加密时会触发 cryptoGetTextPassword，未提供密码时标记 passwordRequested。
     */
    private class OpenCallback(val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {
        var passwordRequested = false

        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}

        override fun cryptoGetTextPassword(): String {
            if (password.isNullOrEmpty()) {
                passwordRequested = true
                throw SevenZipException("Password required to open archive")
            }
            return password
        }
    }
}
