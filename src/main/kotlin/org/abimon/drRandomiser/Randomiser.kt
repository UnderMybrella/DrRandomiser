package org.abimon.drRandomiser

import com.fasterxml.jackson.module.kotlin.readValue
import org.abimon.spiral.core.archives.ArchiveType
import org.abimon.spiral.core.archives.IArchive
import org.abimon.spiral.core.archives.WADArchive
import org.abimon.spiral.core.data.CacheHandler
import org.abimon.spiral.core.data.SpiralData
import org.abimon.spiral.core.formats.OggFormat
import org.abimon.spiral.core.formats.PAKFormat
import org.abimon.spiral.core.formats.SpiralFormat
import org.abimon.spiral.core.formats.TGAFormat
import org.abimon.spiral.core.objects.*
import org.abimon.spiral.mvc.SpiralModel
import org.abimon.spiral.mvc.SpiralModel.Command
import org.abimon.spiral.mvc.startupSpiral
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.FileDataSource
import org.abimon.visi.io.question
import org.abimon.visi.lang.child
import org.abimon.visi.lang.make
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.DosFileAttributeView
import java.util.*
import kotlin.collections.ArrayList

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
        if(!exists())
            mkdir()

        if(Files.getFileAttributeView(toPath(), DosFileAttributeView::class.java) != null)
            Files.setAttribute(toPath(), "dos:hidden", true)
    }

    val randomise = Command("randomise", "operate") { (params) ->
        println("**WARNING**")
        println("The randomise function *will* screw with your data, potentially irreparably.")
        println("It is ***highly*** recommended you make some form of backup")

        if(question("Do you wish to proceed (Y/n)? ", "Y")) {
            val config = randomiserData

            println(config)

            when(operatingArchive.archiveType) {
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

                    if(config.randomiseSprites) {
                        if(config.anarchy) {
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

                    if(config.randomiseMusic) {
                        if(config.anarchy) {
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
            if(format.isFormat(entry))
                return true
            else if(PAKFormat.isFormat(entry)) {
                if(hasFormat(Pak(entry), format))
                    return true
            }
        }

        return false
    }

    fun allOfFormat(pak: Pak, format: SpiralFormat): List<DataSource> {
        val list: MutableList<DataSource> = ArrayList()

        pak.files.forEach { entry ->
            if(format.isFormat(entry))
                list.add(entry)
            else if(PAKFormat.isFormat(entry))
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

    inline fun <reified T: DataSource> List<T>.notExempt(config: RandomiserData): List<T> {
        val reg = config.exempt.map { it.toRegex() }
        when(T::class) {
            WADFileEntry::class -> return filterNot { t -> reg.any { (t as WADFileEntry).name.matches(it) } }
            PakFileEntry::class -> return filterNot { t -> reg.any { (t as PakFileEntry).name.matches(it) } }
            else -> return this
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        DanganronpaRandomiser().enable(SpiralModel.imperator)
        startupSpiral(args)
    }
}