package com.quickbeauty.app

import android.content.Intent
import android.provider.MediaStore

object PhotoPickerIntents {
    fun gallery() = Intent(Intent.ACTION_PICK).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
    fun samsungGallery() = gallery().setPackage("com.sec.android.gallery3d")
    fun localFiles() = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
}
