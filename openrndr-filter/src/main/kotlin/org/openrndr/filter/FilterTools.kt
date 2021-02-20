package org.openrndr.filter

import org.openrndr.resourceUrl

internal class FilterTools

//internal fun filterFragmentCode(resourceId: String): String {
//    val urlString = resourceUrl("gl3/$resourceId", FilterTools::class.java)
//    return URL(urlString).readText()
//}

internal fun filterFragmentUrl(resourceId: String): String {
    return resourceUrl("gl3/$resourceId", FilterTools::class.java)
}