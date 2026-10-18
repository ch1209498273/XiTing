package com.lujinyu.xiting

internal data class PlayerCatalogEntry(val packageName:String,val label:String,
    val hasSession:Boolean=false,val playing:Boolean=false,val canPause:Boolean=false)

/** Installation is visibility, not evidence that an app supports media control. */
internal fun mergePlayerCatalog(installed:List<PlayerCatalogEntry>,sessions:List<PlayerCatalogEntry>,selectedPackage:String?):List<PlayerCatalogEntry> {
    val result=installed.associateBy { it.packageName }.toMutableMap()
    for(session in sessions)result[session.packageName]=session.copy(label=result[session.packageName]?.label ?: session.label)
    return result.values.sortedWith(compareByDescending<PlayerCatalogEntry> { it.packageName==selectedPackage }
        .thenByDescending { it.playing }.thenBy { it.label })
}
