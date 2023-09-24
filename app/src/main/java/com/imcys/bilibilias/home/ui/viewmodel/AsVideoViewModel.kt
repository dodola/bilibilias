package com.imcys.bilibilias.home.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.imcys.asbottomdialog.bottomdialog.AsDialog
import com.imcys.bilibilias.R
import com.imcys.bilibilias.base.model.user.LikeVideoBean
import com.imcys.bilibilias.base.utils.DialogUtils
import com.imcys.bilibilias.base.utils.asToast
import com.imcys.bilibilias.common.base.api.BilibiliApi
import com.imcys.bilibilias.common.base.app.BaseApplication
import com.imcys.bilibilias.common.base.app.BaseApplication.Companion.asUser
import com.imcys.bilibilias.common.base.constant.BILIBILI_URL
import com.imcys.bilibilias.common.base.constant.COOKIE
import com.imcys.bilibilias.common.base.constant.COOKIES
import com.imcys.bilibilias.common.base.constant.REFERER
import com.imcys.bilibilias.common.base.extend.launchIO
import com.imcys.bilibilias.common.base.extend.launchUI
import com.imcys.bilibilias.common.base.utils.file.FileUtils
import com.imcys.bilibilias.common.base.utils.http.HttpUtils
import com.imcys.bilibilias.common.base.utils.http.KtHttpUtils
import com.imcys.bilibilias.danmaku.change.DmXmlToAss
import com.imcys.bilibilias.home.ui.activity.AsVideoActivity
import com.imcys.bilibilias.home.ui.model.*
import com.microsoft.appcenter.analytics.Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream

/**
 * 解析视频的ViewModel
 */

class AsVideoViewModel : ViewModel() {

    /**
     * 缓存视频
     * @param videoBaseBean VideoBaseBean
     * @param videoPageListData VideoPageListData
     */
    fun downloadVideo(
        view: View,
        videoBaseBean: VideoBaseBean,
        videoPageListData: VideoPageListData,
    ) {
        val context = view.context
        val loadDialog = DialogUtils.loadDialog(context).apply { show() }

        viewModelScope.launchIO {
            if ((context as AsVideoActivity).userBaseBean.data.level >= 2) {
                val dashVideoPlayBean = KtHttpUtils.addHeader(
                    COOKIE,
                    BaseApplication.dataKv.decodeString(COOKIES, "")!!,
                )
                    .addHeader(REFERER, BILIBILI_URL)
                    .asyncGet<DashVideoPlayBean>("${BilibiliApi.videoPlayPath}?bvid=${context.bvid}&cid=${context.cid}&qn=64&fnval=4048&fourk=1")
                // 这里再检验一次，是否为404内容
                loadDialog.cancel()
                launchUI {
                    if (dashVideoPlayBean.code == 0) {
                        DialogUtils.downloadVideoDialog(
                            context,
                            videoBaseBean,
                            videoPageListData,
                            dashVideoPlayBean,
                        ).show()
                    }
                }
            } else {
                launchUI {
                    AsDialog.init(context).build {
                        config = {
                            title = "止步于此"
                            content =
                                "鉴于你的账户未转正，请前往B站完成答题，否则无法为您提供缓存服务。\n" +
                                "作者也是B站UP主，见到了许多盗取视频现象，更有甚者缓存番剧后发布内容到其他平台。\n" +
                                "而你的账户甚至是没有转正的，bilibilias自然不会想提供服务。"
                            positiveButtonText = "知道了"
                            positiveButton = {
                                it.cancel()
                            }
                        }
                    }.show()
                }
            }
        }
    }

    /**
     * 缓存番剧
     * @param videoBaseBean VideoBaseBean
     * @param bangumiSeasonBean BangumiSeasonBean
     */
    fun downloadBangumiVideo(
        view: View,
        videoBaseBean: VideoBaseBean,
        bangumiSeasonBean: BangumiSeasonBean,
    ) {
        val context = view.context

        val loadDialog = DialogUtils.loadDialog(context).apply { show() }

        viewModelScope.launchIO {
            if ((context as AsVideoActivity).userBaseBean.data.level >= 2) {
                val dashVideoPlayBean =
                    KtHttpUtils.addHeader(
                        COOKIE,
                        BaseApplication.dataKv.decodeString(COOKIES, "")!!,
                    )
                        .addHeader(REFERER, BILIBILI_URL)
                        .asyncGet<DashVideoPlayBean>("${BilibiliApi.videoPlayPath}?bvid=${context.bvid}&cid=${context.cid}&qn=64&fnval=4048&fourk=1")
                loadDialog.cancel()
                launchUI {
                    if (dashVideoPlayBean.code == 0) {
                        DialogUtils.downloadVideoDialog(
                            context,
                            videoBaseBean,
                            bangumiSeasonBean,
                            dashVideoPlayBean,
                        ).show()
                    }
                }
            } else {
                launchUI {
                    AsDialog.init(context).build {
                        config = {
                            title = "止步于此"
                            content =
                                "鉴于你的账户未转正，请前往B站完成答题，否则无法为您提供缓存服务。\n" +
                                "作者也是B站UP主，见到了许多盗取视频现象，更有甚者缓存番剧后发布内容到其他平台。\n" +
                                "而你的账户甚至是没有转正的，bilibilias自然不会想提供服务。"
                            positiveButtonText = "知道了"
                            positiveButton = {
                                it.cancel()
                            }
                        }
                    }.show()
                }
            }
        }
    }

    fun downloadDanMu(view: View, videoBaseBean: VideoBaseBean) {
        val context = view.context

        DialogUtils.downloadDMDialog(view.context, videoBaseBean) { binding ->
            viewModelScope.launchIO {
                val response =
                    HttpUtils.asyncGet("${BilibiliApi.videoDanMuPath}?oid=${(context as AsVideoActivity).cid}")

                when (binding.dialogDlDmTypeRadioGroup.checkedRadioButtonId) {
                    R.id.dialog_dl_dm_ass -> {
                        saveAssDanmaku(
                            context,
                            response.await().body!!.bytes(),
                            videoBaseBean,
                        )
                    }

                    R.id.dialog_dl_dm_xml -> {
                        saveDanmaku(context, response.await().body!!.bytes(), videoBaseBean)
                    }

                    else -> throw Exception("意外的选项")
                }
            }
        }.show()
    }

    private fun saveAssDanmaku(
        context: AsVideoActivity,
        bytes: ByteArray,
        videoBaseBean: VideoBaseBean,
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val savePath = sharedPreferences.getString(
            "user_download_save_path",
            context.getExternalFilesDir("download").toString(),
        )
        val fileName = "$savePath/${(context.bvid)}/${context.cid}_danmu.ass"
        val assFile = File(fileName)

        val folderFile = File("$savePath/${(context.bvid)}")
        // 检查是否存在文件夹
        if (!folderFile.exists()) folderFile.mkdirs()

        if (!FileUtils.isFileExists(assFile)) assFile.createNewFile()

        val decompressBytes =
            context.decompress(bytes) // 调用解压函数进行解压，返回包含解压后数据的byte数组

        val outputStream = FileOutputStream(assFile)
        decompressBytes.also {
            outputStream.write(it)
            it.clone()
        }

        assFile.writeText(
            DmXmlToAss.xmlToAss(
                assFile.readText(),
                videoBaseBean.data.title + context.cid,
                "1920",
                "1080",
                context,
            ),
        )

        viewModelScope.launchUI {
            asToast(
                context,
                "下载弹幕储存于\n$fileName",
            )
            // 通知下载成功
            Analytics.trackEvent(context.getString(R.string.download_barrage))
        }
    }

    fun saveDanmaku(context: AsVideoActivity, bytes: ByteArray, videoBaseBean: VideoBaseBean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val savePath = sharedPreferences.getString(
            "user_download_save_path",
            context.getExternalFilesDir("download").toString(),
        )

        val bufferedSink: BufferedSink?

        val dest = File("$savePath/${(context.bvid)}/${context.cid}_danmu.xml")
        // 检查是否存在文件夹
        val parentDir = dest.parentFile
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()
        if (!FileUtils.isFileExists(dest)) {
            File("$savePath/${(context.bvid)}/${context.cid}_danmu.xml").createNewFile()
        }

        val sink = dest.sink() // 打开目标文件路径的sink
        val decompressBytes =
            context.decompress(bytes) // 调用解压函数进行解压，返回包含解压后数据的byte数组
        bufferedSink = sink.buffer()
        decompressBytes.let { bufferedSink.write(it) } // 将解压后数据写入文件（sink）中
        bufferedSink.close()

        viewModelScope.launchUI {
            asToast(
                context,
                "下载弹幕储存于\n$savePath/${(context.bvid)}/${context.cid}_danmu.xml",
            )
            // 通知下载成功
            Analytics.trackEvent(context.getString(R.string.download_barrage))
        }
    }

    /**
     * 点赞视频
     * @param bvid String aid
     */
    fun likeVideo(view: View, bvid: String) {
        val context = view.context

        viewModelScope.launchIO {
            val likeVideoBean =
                KtHttpUtils.addHeader(
                    COOKIE,
                    BaseApplication.dataKv.decodeString(COOKIES, "")!!,
                )
                    .addParam("csrf", BaseApplication.dataKv.decodeString("bili_jct", "")!!)
                    .addParam("like", "1")
                    .addParam("bvid", bvid)
                    .asyncPost<LikeVideoBean>(BilibiliApi.videLikePath)

            if ((context as AsVideoActivity).binding.archiveHasLikeBean?.data == 0) {
                launchUI {
                    when (likeVideoBean.code) {
                        0 -> {
                            context.binding.archiveHasLikeBean?.data = 1
                            context.binding.asVideoLikeBt.isSelected = true
                        }

                        65006 -> {
                            cancelLikeVideo(view, bvid)
                        }

                        else -> {
                            asToast(context, likeVideoBean.message)
                        }
                    }
                }
            } else {
                cancelLikeVideo(view, bvid)
            }
        }
    }

    /**
     * 取消对视频的点赞
     * @param bvid String
     */
    private fun cancelLikeVideo(view: View, bvid: String) {
        val context = view.context

        viewModelScope.launchIO {
            val likeVideoBean =
                KtHttpUtils.addHeader(
                    COOKIE,
                    BaseApplication.dataKv.decodeString(COOKIES, "")!!,
                )
                    .addParam("csrf", BaseApplication.dataKv.decodeString("bili_jct", "") ?: "")
                    .addParam("like", "2")
                    .addParam("bvid", bvid)
                    .asyncPost<LikeVideoBean>(BilibiliApi.videLikePath)

            launchUI {
                when (likeVideoBean.code) {
                    0 -> {
                        (context as AsVideoActivity).binding.apply {
                            archiveHasLikeBean?.data = 0
                            asVideoLikeBt.isSelected = false
                        }
                    }

                    65004 -> {
                        likeVideo(view, bvid)
                    }

                    else -> {
                        asToast(context, likeVideoBean.message)
                    }
                }
            }
        }
    }

    /**
     * 视频投币
     * @param bvid String
     */
    fun videoCoinAdd(view: View, bvid: String) {
        val context = view.context

        viewModelScope.launchIO {
            KtHttpUtils
                .addHeader(COOKIE, BaseApplication.dataKv.decodeString(COOKIES, "")!!)
                .addParam("bvid", bvid)
                .addParam("multiply", "2")
                .addParam("csrf", BaseApplication.dataKv.decodeString("bili_jct", "")!!)
                .asyncPost<VideoCoinAddBean>(BilibiliApi.videoCoinAddPath)

            launchUI() {
                (context as AsVideoActivity).binding.archiveCoinsBean?.multiply = 2
                context.binding.asVideoThrowBt.isSelected = true
            }
        }
    }

    /**
     * 加载用户收藏夹
     */
    @SuppressLint("NotifyDataSetChanged")
    fun loadCollectionView(view: View, avid: Long) {
        val context = view.context
        (context as AsVideoActivity).binding.apply {
            viewModelScope.launchIO {
                val userCreateCollectionBean =
                    KtHttpUtils.addHeader(
                        COOKIE,
                        BaseApplication.dataKv.decodeString(COOKIES, "")!!,
                    )
                        .asyncGet<UserCreateCollectionBean>(BilibiliApi.userCreatedScFolderPath + "?up_mid=" + asUser.mid)

                launchUI {
                    if (userCreateCollectionBean.code == 0) {
                        DialogUtils.loadUserCreateCollectionDialog(
                            context,
                            userCreateCollectionBean,
                            { _, _ ->
                            },
                            { selects ->
                                // 选取完成了收藏文件夹
                                setCollection(context, selects, avid)
                            },
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * 复制内容
     * @param inputStr String
     */
    fun addClipboardMessage(view: View, inputStr: String): Boolean {
        val context = view.context

        val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // When setting the clip board text.
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", inputStr))
        // Only show a toast for Android 12 and lower.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, context.getString(R.string.Copied), Toast.LENGTH_SHORT).show()
        }

        return true
    }

    /**
     * 设置收藏夹的ID列表
     * @param selects MutableList<Long>
     */
    private fun setCollection(context: AsVideoActivity, selects: MutableList<Long>, avid: Long) {
        var addMediaIds = ""
        selects.forEachIndexed { index, l ->
            if (index == selects.size) {
                addMediaIds = "$addMediaIds$l"
            }
            addMediaIds = "$addMediaIds,$l"
        }
        addCollection(context, addMediaIds, avid)
    }

    /**
     * 新增收藏夹内容
     * @param addMediaIds String
     */
    private fun addCollection(context: AsVideoActivity, addMediaIds: String, avid: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val collectionResultBean =
                KtHttpUtils.addHeader(COOKIE, asUser.cookie)
                    .addParam("rid", avid.toString())
                    .addParam("add_media_ids", addMediaIds)
                    .addParam("csrf", BaseApplication.dataKv.decodeString("bili_jct", "")!!)
                    .addParam("type", "2")
                    .asyncPost<CollectionResultBean>(BilibiliApi.videoCollectionSetPath)

            if (collectionResultBean.code == 0) {
                context.binding.archiveFavouredBean?.isFavoured = true
                context.binding.asVideoCollectionBt.isSelected = true
            } else {
                asToast(context, "收藏失败${collectionResultBean.code}")
            }
        }
    }
}