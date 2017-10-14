package org.abimon.drRandomiser

import com.fasterxml.jackson.module.kotlin.readValue
import org.abimon.drRandomiser.Randomiser.notExempt
import org.abimon.spiral.core.archives.ArchiveType
import org.abimon.spiral.core.archives.FlatFileArchive
import org.abimon.spiral.core.archives.IArchive
import org.abimon.spiral.core.archives.WADArchive
import org.abimon.spiral.core.data.CacheHandler
import org.abimon.spiral.core.data.SpiralData
import org.abimon.spiral.core.formats.*
import org.abimon.spiral.core.objects.*
import org.abimon.spiral.core.write
import org.abimon.spiral.mvc.SpiralModel
import org.abimon.spiral.mvc.SpiralModel.Command
import org.abimon.spiral.mvc.startupSpiral
import org.abimon.spiral.util.OffsetInputStream
import org.abimon.spiral.util.debug
import org.abimon.spiral.util.trace
import org.abimon.visi.collections.remove
import org.abimon.visi.io.*
import org.abimon.visi.lang.and
import org.abimon.visi.lang.child
import org.abimon.visi.lang.isRegex
import org.abimon.visi.lang.make
import org.abimon.visi.util.zip.forEach
import java.awt.Dimension
import java.awt.geom.Dimension2D
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.DosFileAttributeView
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

@Suppress("unused")
object Randomiser {
    val randomiserData: RandomiserData
        get() = SpiralData.MAPPER.readValue(SpiralData.MAPPER.writeValueAsString(SpiralModel.getPluginData("XD-SO-RANDOM") ?: emptyMap<String, Any>()))

    val operatingArchive: IArchive
        get() = IArchive(SpiralModel.operating ?: throw IllegalStateException("Attempt to get the archive while operating is null, this is a bug!")) ?: throw IllegalStateException("Attempts to create an archive return null, this is a bug!")
    val operatingName: String
        get() = SpiralModel.operating?.nameWithoutExtension ?: ""

    val random: Random = Random()

    val RANDOMISER_CACHE = File(".randomiser_cache").apply {
        if (!exists())
            mkdir()

        if (Files.getFileAttributeView(toPath(), DosFileAttributeView::class.java) != null)
            Files.setAttribute(toPath(), "dos:hidden", true)
    }

    fun cacheFile(dataSource: DataSource, name: String): DataSource {
        val cached = File(RANDOMISER_CACHE, name)

        if (!cached.exists()) {
            cached.parentFile.mkdirs()
            cached.outputStream().use(dataSource::pipe)
        }

        return FileDataSource(cached)
    }

    val randomise = Command("randomise", "operate") { (params) ->
        println("**WARNING**")
        println("The randomise function *will* screw with your data, potentially irreparably.")
        println("It is ***highly*** recommended you make some form of backup")

        if (question("Do you wish to proceed (Y/n)? ", "Y")) {
            val config = randomiserData

            println(config)

            when (operatingArchive.archiveType) {
                ArchiveType.WAD -> {
                    val wad = (operatingArchive as WADArchive)
                    val customWad = make<CustomWAD> { wad(wad.wad) }
                    val cache: List<WADFileEntry>.() -> Unit = {
                        this.forEach { entry ->
                            val cached = File(RANDOMISER_CACHE, entry.name)

                            if (cached.exists())
                                return@forEach

                            cached.parentFile.mkdirs()
                            cached.outputStream().use(entry::pipe)
                        }
                    }

                    if (config.randomiseSprites) {
                        if (config.anarchySprites) {
                            val sprites = wad.wad.files.filter { (name) -> name.endsWith(".tga") }.notExempt(config)
                            val spritesInPAKs = wad.wad.files.filter { (name) -> name.endsWith(".pak") }.filter { entry -> hasFormat(Pak(entry), TGAFormat) }.notExempt(config)
                            sprites.cache()
                            spritesInPAKs.cache()

                            val spriteFiles: MutableList<DataSource> = ArrayList()
                            spriteFiles.addAll(sprites.map { (name) -> FileDataSource(File(RANDOMISER_CACHE, name)) })
                            spriteFiles.addAll(spritesInPAKs.flatMap { (name) -> allOfFormat(Pak(FileDataSource(File(RANDOMISER_CACHE, name))), TGAFormat) })

                            sprites.forEach { (name) -> customWad.data(name, spriteFiles.removeAt(random.nextInt(spriteFiles.size))) }
                            spritesInPAKs.forEach { entry ->
                                val (cacheOut, cacheIn) = CacheHandler.cacheStream()

                                val customPak = replaceAllOfFormat(Pak(entry), TGAFormat, spriteFiles)

                                cacheOut.use(customPak::compile)

                                customWad.data(entry.name, cacheIn)
                            }
                        } else {
                            val bustups = wad.wad.files.filter { entry -> entry.name.child.matches("bustup_\\d+_\\d+\\.tga".toRegex()) }.notExempt(config)
                            val stands = wad.wad.files.filter { entry -> entry.name.child.matches("stand_\\d+_\\d+\\.tga".toRegex()) }.notExempt(config)

                            bustups.cache()
                            stands.cache()

                            bustups.forEach { (name) -> customWad.file(File(RANDOMISER_CACHE, bustups[random.nextInt(bustups.size)].name), name) }
                            stands.forEach { (name) -> customWad.file(File(RANDOMISER_CACHE, stands[random.nextInt(stands.size)].name), name) }
                        }
                    }

                    if (config.randomiseMusic) {
                        if (config.anarchyMusic) {
                            val sounds = wad.wad.files.filter { (name) -> name.endsWith(".ogg") }.notExempt(config)
                            val soundsInPAKs = wad.wad.files.filter { (name) -> name.endsWith(".pak") }.filter { entry -> hasFormat(Pak(entry), OggFormat) }.notExempt(config)
                            sounds.cache()
                            soundsInPAKs.cache()

                            val soundFiles: MutableList<DataSource> = ArrayList()
                            soundFiles.addAll(sounds.map { (name) -> FileDataSource(File(RANDOMISER_CACHE, name)) })
                            soundFiles.addAll(soundsInPAKs.flatMap { (name) -> allOfFormat(Pak(FileDataSource(File(RANDOMISER_CACHE, name))), OggFormat) })

                            sounds.forEach { (name) -> customWad.data(name, soundFiles.removeAt(random.nextInt(soundFiles.size))) }
                            soundsInPAKs.forEach { entry ->
                                val (cacheOut, cacheIn) = CacheHandler.cacheStream()

                                val customPak = replaceAllOfFormat(Pak(entry), OggFormat, soundFiles)

                                cacheOut.use(customPak::compile)

                                customWad.data(entry.name, cacheIn)
                            }
                        } else {
                            val bgm = wad.wad.files.filter { entry -> entry.name.child.matches("dr\\d_bgm_hca.awb.\\d+\\.ogg".toRegex()) }.notExempt(config)
                            val movie = wad.wad.files.filter { entry -> entry.name.child.matches("movie_\\d+\\.ogg".toRegex()) }.notExempt(config)

                            bgm.cache()
                            movie.cache()

                            bgm.forEach { (name) -> customWad.file(File(RANDOMISER_CACHE, bgm[random.nextInt(bgm.size)].name), name) }
                            movie.forEach { (name) -> customWad.file(File(RANDOMISER_CACHE, movie[random.nextInt(movie.size)].name), name) }
                        }
                    }

//                    if(config.randomise.isNotEmpty()) {
//                        for(pool in config.randomise) {
//                            val randomPool = pool.map { path -> wad.wad.files.resolvePath(path) }.filterNotNull()
//
//
//                        }
//                    }

                    val time = measureTimeMillis {
                        val tmpFile = File(SpiralModel.operating!!.absolutePath + ".tmp")
                        val backupFile = File(SpiralModel.operating!!.absolutePath + ".backup")
                        try {
                            FileOutputStream(tmpFile).use(customWad::compile)

                            if (backupFile.exists()) backupFile.delete()
                            SpiralModel.operating!!.renameTo(backupFile)
                            tmpFile.renameTo(SpiralModel.operating!!)
                        } finally {
                            tmpFile.delete()
                        }
                    }

                    println("Finished compiling in $time ms")
                }
                ArchiveType.FLAT_FILE -> {
                    val flatFile = (operatingArchive as FlatFileArchive)
                    val cache: List<Pair<String, DataSource>>.() -> Unit = {
                        this.forEach { (name, data) ->
                            val cached = File(RANDOMISER_CACHE, name)

                            if (cached.exists())
                                return@forEach

                            cached.parentFile.mkdirs()
                            cached.outputStream().use(data::pipe)
                        }
                    }
                    val cacheAndReplace: List<Pair<String, DataSource>>.() -> List<Pair<String, DataSource>> = {
                        this.map { (name, data) ->
                            if(!SPCFormat.isFormat(data))
                                return@map name to data

                            val cached = File(RANDOMISER_CACHE, "$name-decomp.dat")

                            if (cached.exists())
                                return@map name to FileDataSource(cached)

                            cached.parentFile.mkdirs()

                            val customSPC = make<CustomSPC> {
                                SPC(data).files.forEach { entry -> file(entry.name, entry) }
                            }

                            cached.outputStream().use(customSPC::compile)

                            return@map name to FileDataSource(cached)
                        }
                    }

                    if (config.randomiseSprites) {
                        if (config.anarchySprites) {
                            if (!question("Note: V3's anarchy mode is incomplete, and will take forever and consume large amounts of disk space!\nDo you wish to proceed? (Y/n)? ", "Y"))
                                return@Command

                            val textureFiles = flatFile.fileEntries.filter { (name, dataSource) -> (name.endsWith("spc") || name.endsWith("SPC")) && SPC(dataSource).files.run { any { entry -> entry.name.endsWith("srd") } && any { entry -> entry.name.endsWith("srdv") } } }.pairsNotExempt(config).cacheAndReplace()
                            val textures = textureFiles.flatMap { (dataName, dataSource) ->
                                val images: MutableList<Triple<Int, Dimension2D, Array<DataSource>>> = ArrayList()
                                val spc = SPC(dataSource)

                                val files = spc.files.groupBy { entry -> entry.name.endsWith("srd") }
                                val srds = files[true] ?: emptyList()
                                val others = files[false]!!.toMutableList()

                                srds.forEach loop@{ srdEntry ->
                                    val img: DataSource
                                    if (others.any { entry -> entry.name == srdEntry.name.split('.')[0] + ".srdv" })
                                        img = others.remove { entry -> entry.name == srdEntry.name.split('.')[0] + ".srdv" } ?: return@loop
                                    else
                                        img = others.firstOrNull { entry -> entry.name == srdEntry.name.split('.')[0] + ".srdi" } ?: return@loop

                                    val srd = SRD(srdEntry)
                                    srd.items.forEach { item ->
                                        if (item !is TXRItem)
                                            return@forEach

                                        val imgMipmaps: MutableList<DataSource> = ArrayList(item.mipmaps.size)
                                        img.seekableUse { seek ->
                                            item.mipmaps.forEachIndexed { index, (start, len) ->
                                                seek.reset()
                                                seek.skip(start.toLong())

                                                val (imgOut, imgIn) = CacheHandler.cacheStream()
                                                val imgData = ByteArray(len)
                                                seek.read(imgData)
                                                imgOut.use(imgData::write)

                                                imgMipmaps.add(imgIn)
                                            }
                                        }
                                        images.add(item.format to Dimension(item.dispWidth, item.dispHeight) and imgMipmaps.toTypedArray())
                                    }
                                }

                                return@flatMap images
                            }

                            val newEntries: MutableList<Pair<String, DataSource>> = ArrayList()

                            textureFiles.forEach { (name, data) ->
                                val spc = SPC(data)

                                val customSPC = make<CustomSPC> {
                                    spc.files.forEach { fileEntry -> file(fileEntry.name, fileEntry) }
                                }

                                val files = spc.files.groupBy { entry -> entry.name.endsWith("srd") }
                                val srds = files[true] ?: emptyList()
                                val others = files[false]!!.toMutableList()

                                srds.forEach loop@{ srdEntry ->
                                    val img = others.remove { entry -> entry.name == srdEntry.name.split('.')[0] + ".srdv" } ?: return@loop
                                    val imgName = srdEntry.name.split('.')[0] + ".srdv"

                                    val customSRD = CustomSRD(srdEntry, img)
                                    SRD(srdEntry).items.forEach srdFor@{ item ->
                                        if(item !is TXRItem)
                                            return@srdFor

                                        val (format, size, mips) = textures[random.nextInt(textures.size)]
                                        customSRD.mipmap(item.name, format, size, mips)
                                    }

                                    val (srdOutS, srdIn) = CacheHandler.cacheStream()
                                    val (srdvOutS, srdvIn) = CacheHandler.cacheStream()

                                    srdOutS.use { srdOut -> srdvOutS.use { srdvOut -> customSRD.patch(srdOut, srdvOut) } }

                                    customSPC.file(srdEntry.name, srdIn)
                                    customSPC.file(imgName, srdvIn)
                                }

                                val (spcOut, spcIn) = CacheHandler.cacheStream()

                                spcOut.use(customSPC::compile)

                                newEntries.add(name to spcIn)
                            }

                            flatFile.compile(newEntries)
                        } else {
                            val bustups = flatFile.fileEntries.filter { (name) -> name.child.matches("bustup_\\d+_\\d+\\.spc".toRegex()) }.pairsNotExempt(config)
                            val stands = flatFile.fileEntries.filter { (name) -> name.child.matches("stand_\\d+_\\d+\\.SPC".toRegex()) }.pairsNotExempt(config)

                            bustups.cache()
                            stands.cache()

                            val newEntries: MutableList<Pair<String, DataSource>> = ArrayList()

                            bustups.forEach { (name) -> newEntries.add(name to FileDataSource(File(RANDOMISER_CACHE, bustups[random.nextInt(bustups.size)].first))) }
                            stands.forEach { (name) -> newEntries.add(name to FileDataSource(File(RANDOMISER_CACHE, stands[random.nextInt(stands.size)].first))) }

                            flatFile.compile(newEntries)
                        }
                    }
                }
            }
        } else {
            println("Aborted randomisation process")
        }
    }

    val resetConfig = Command("reset_randomiser_config") {
        SpiralModel.putPluginData("XD-SO-RANDOM", RandomiserData())
        println("Reset Randomiser config to default.")
    }

    fun hasFormat(pak: Pak, format: SpiralFormat): Boolean {
        pak.files.forEach { entry ->
            if (format.isFormat(entry))
                return true
            else if (PAKFormat.isFormat(entry)) {
                if (hasFormat(Pak(entry), format))
                    return true
            }
        }

        return false
    }

    fun allOfFormat(pak: Pak, format: SpiralFormat): List<DataSource> {
        val list: MutableList<DataSource> = ArrayList()

        pak.files.forEach { entry ->
            if (format.isFormat(entry))
                list.add(entry)
            else if (PAKFormat.isFormat(entry))
                list.addAll(allOfFormat(Pak(entry), format))
        }

        return list
    }

    fun replaceAllOfFormat(pak: Pak, format: SpiralFormat, replacable: MutableList<DataSource>): CustomPak {
        val customPak = CustomPak()

        pak.files.forEach { entry ->
            when {
                format.isFormat(entry) -> customPak.dataSource(replacable.removeAt(random.nextInt(replacable.size)))
                PAKFormat.isFormat(entry) -> {
                    val (pakOut, pakIn) = CacheHandler.cacheStream()
                    pakOut.use(replaceAllOfFormat(Pak(entry), format, replacable)::compile)
                    customPak.dataSource(pakIn)
                }
                else -> customPak.dataSource(entry)
            }
        }

        return customPak
    }

    val DataSource.localName: String
        get() {
            when (this) {
                is WADFileEntry -> return this.name
                is PakFileEntry -> return this.name
                else -> return ""
            }
        }

    fun List<WADFileEntry>.resolvePath(path: String): DataSource? {
        val regex = if (path.isRegex()) path.toRegex() else null
        val direct = firstOrNull { (name) -> name == path || (regex != null && name.matches(regex)) }

        if (direct != null)
            return direct

        val pak = firstOrNull { (name) -> path.startsWith("$name/") } ?: return null

        if (PAKFormat.isFormat(pak)) {
            val components = path.replace("${pak.name}/", "").split('/')

            var entry: DataSource = pak

            for (filename in components) {
                if (!PAKFormat.isFormat(entry)) return null
                entry = Pak(entry).files.firstOrNull { (name) -> name == filename } ?: return null
            }

            return entry
        }

        return null
    }

    inline fun <reified T : DataSource> List<T>.notExempt(config: RandomiserData): List<T> {
        val reg = config.exempt.map { it.toRegex() }
        when (T::class) {
            WADFileEntry::class -> return filterNot { t -> reg.any { (t as WADFileEntry).name.matches(it) } }
            PakFileEntry::class -> return filterNot { t -> reg.any { (t as PakFileEntry).name.matches(it) } }
            else -> return this
        }
    }

    fun List<Pair<String, DataSource>>.pairsNotExempt(config: RandomiserData): List<Pair<String, DataSource>> {
        val reg = config.exempt.map { it.toRegex() }
        return filterNot { (name) -> reg.any { name.matches(it) } }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        DanganronpaRandomiser().enable(SpiralModel.imperator)
        startupSpiral(args)
    }
}