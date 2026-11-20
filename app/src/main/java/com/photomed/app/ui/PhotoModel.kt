package com.photomed.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.PhotoEdits

/** Transformación de Coil que aplica la edición guardada de una foto (giro, espejo, recorte). */
class EditTransformation(private val edit: ImageEdit) : Transformation {
    override val cacheKey: String = "edit:${edit.rot}:${edit.mirror}:${edit.crop?.joinToString(",")}"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = edit.apply(input)
}

/** Modelo para mostrar una foto con su edición (la URI tal cual si no tiene). */
fun photoModel(context: Context, uri: Uri): Any {
    val e = PhotoEdits.of(context, uri.toString())
    return if (e.isIdentity) uri else ImageRequest.Builder(context).data(uri).transformations(EditTransformation(e)).build()
}
