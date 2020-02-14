/**
 * Copyright (C) 2020 Chenhe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cc.chenhe.weargallery.uilts.diskcache

import okio.Okio
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

private const val APP_VERSION = 1L
private const val MAX_SIZE = 1000L //1000B

private const val KEY = "test_key"
private const val DATA = "Test Data"

class DiskLruCacheTest {

    private lateinit var cacheFile: File
    private lateinit var cache: DiskLruCache

    @Before
    fun setup() {
        cacheFile = File(System.getProperty("java.io.tmpdir"), "wg_cache_test")
        clean()
        cache = DiskLruCache.open(cacheFile, APP_VERSION, MAX_SIZE)
    }

    @After
    fun clean() {
        if (cacheFile.isDirectory && cacheFile.exists()) {
            cacheFile.deleteRecursively()
        }
    }

    private fun writeCache(key: String, data: String, commit: Boolean = true): DiskLruCache.Editor {
        val editor = cache.edit(key)!!
        Okio.sink(editor.getTargetFile()).use {
            Okio.buffer(it).use { bs ->
                bs.writeUtf8(data)
            }
        }
        if (commit) {
            editor.commit()
        }
        return editor
    }

    private fun writeCacheBytes(key: String, size: Int) {
        val editor = cache.edit(key)!!
        Okio.sink(editor.getTargetFile()).use {
            Okio.buffer(it).use { bs ->
                bs.write(ByteArray(size))
            }
        }
        editor.commit()
    }

    @Test
    fun getFile_noCache_null() {
        cache.getFile("no").shouldBeNull()
    }

    @Test
    fun edit_createCache() {
        writeCache(KEY, DATA)

        val cached = cache.getFile(KEY)
        cached.shouldNotBeNull()
        Okio.source(cached).use {
            Okio.buffer(it).use { bs ->
                bs.readUtf8() shouldBeEqualTo DATA
            }
        }
    }

    @Test
    fun edit_abort() {
        writeCache(KEY, DATA, false).abort()
        cache.getFile(KEY).shouldBeNull()
    }

    @Test
    fun remove_alreadyExist() {
        writeCache(KEY, DATA)
        cache.remove(KEY)
        cache.getFile(KEY).shouldBeNull()
    }

    @Test
    fun remove_notExist() {
        cache.remove(KEY)
        cache.getFile(KEY).shouldBeNull()
    }

    @Test
    fun edit_lru() {
        // 5*100 = 500B
        val size = 100L
        for (i in 0..4) {
            writeCacheBytes(i.toString(), size.toInt())
        }
        for (i in 0..4) {
            val cached = cache.getFile(i.toString())
            cached.shouldNotBeNull()
            cached.length() shouldBeEqualTo size
        }

        cache.getFile("2")
        cache.getFile("4")
        cache.getFile("1")
        writeCacheBytes("big", 800)
        Thread.sleep(50)
        // queue: [0,3,2],4,1,big
        for (i in arrayOf(0, 3, 2)) {
            cache.getFile(i.toString()).shouldBeNull()
        }
        for (i in arrayOf(4, 1)) {
            cache.getFile(i.toString()).let {
                it.shouldNotBeNull()
                it.length() shouldBeEqualTo size
            }
        }
        cache.getFile("big").let {
            it.shouldNotBeNull()
            it.length() shouldBeEqualTo 800L
        }
    }
}