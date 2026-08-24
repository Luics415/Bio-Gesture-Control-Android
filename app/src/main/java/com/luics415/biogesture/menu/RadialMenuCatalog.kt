package com.luics415.biogesture.menu

/** Stable identifiers for every radial-menu level. */
enum class MenuLevelId {
    PRINCIPAL,
    CONFIG,
    EDIT,
    WEB,
    MEDIA,
    PLAY,
    VOLUME,
    NAV,
}

/** Actions that can leave the menu controller and be executed by Android. */
enum class MenuActionId {
    OPEN_SETTINGS,
    OPEN_APP_PERMISSIONS,
    TOGGLE_HAND_SKELETON,
    RESET_CALIBRATION,

    COPY,
    PASTE,
    SELECT_ALL,
    CUT,

    WEB_BACK,
    WEB_FORWARD,
    SCROLL_UP,
    SCROLL_DOWN,
    NEW_TAB,
    REFRESH,
    CLOSE_TAB,

    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_FULLSCREEN,
    MEDIA_FORWARD_10_SECONDS,
    MEDIA_BACK_10_SECONDS,
    MEDIA_PLAY,
    MEDIA_PAUSE,

    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,

    SYSTEM_BACK,
    SYSTEM_HOME,
    SYSTEM_RECENTS,
    SYSTEM_NOTIFICATIONS,
}

/**
 * An item identifier also describes its navigation behavior. This removes the
 * previous string comparisons and placeholder values such as "NULL".
 */
sealed interface MenuItemId {
    data class OpenLevel(val level: MenuLevelId) : MenuItemId
    data class RunAction(val action: MenuActionId) : MenuItemId
    data object Back : MenuItemId
    data object Close : MenuItemId
}

data class RadialMenuItem(
    val id: MenuItemId,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "A radial-menu item must have a label." }
    }
}

data class RadialMenuDefinition(
    val level: MenuLevelId,
    val parent: MenuLevelId?,
    val items: List<RadialMenuItem>,
) {
    init {
        require(items.isNotEmpty()) { "A radial-menu level cannot be empty." }
        require(items.map { it.id }.distinct().size == items.size) {
            "A radial-menu level cannot contain duplicate item IDs."
        }
    }
}

class RadialMenuCatalog private constructor(
    private val definitions: Map<MenuLevelId, RadialMenuDefinition>,
) {
    init {
        require(definitions.keys == MenuLevelId.entries.toSet()) {
            "Every MenuLevelId must have exactly one definition."
        }
        require(definitions.values.all { it.level == MenuLevelId.PRINCIPAL || it.parent != null }) {
            "Every submenu must define a parent."
        }
    }

    fun definition(level: MenuLevelId): RadialMenuDefinition =
        checkNotNull(definitions[level]) { "Missing menu definition for $level." }

    fun allDefinitions(): List<RadialMenuDefinition> = MenuLevelId.entries.map(::definition)

    companion object {
        val DEFAULT: RadialMenuCatalog = RadialMenuCatalog(
            listOf(
                menu(
                    MenuLevelId.PRINCIPAL,
                    parent = null,
                    item(MenuItemId.OpenLevel(MenuLevelId.CONFIG), "CONFIG"),
                    item(MenuItemId.OpenLevel(MenuLevelId.EDIT), "EDIT"),
                    item(MenuItemId.OpenLevel(MenuLevelId.WEB), "WEB"),
                    item(MenuItemId.OpenLevel(MenuLevelId.MEDIA), "MEDIA"),
                    item(MenuItemId.OpenLevel(MenuLevelId.PLAY), "PLAY"),
                    item(MenuItemId.OpenLevel(MenuLevelId.VOLUME), "VOLUME"),
                    item(MenuItemId.OpenLevel(MenuLevelId.NAV), "NAV"),
                    item(MenuItemId.Close, "BACK"),
                ),
                menu(
                    MenuLevelId.CONFIG,
                    item(MenuItemId.RunAction(MenuActionId.OPEN_SETTINGS), "AJUSTES"),
                    item(MenuItemId.RunAction(MenuActionId.OPEN_APP_PERMISSIONS), "PERMISOS"),
                    item(MenuItemId.RunAction(MenuActionId.TOGGLE_HAND_SKELETON), "GESTOS"),
                    item(MenuItemId.RunAction(MenuActionId.RESET_CALIBRATION), "CALIBRAR"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.EDIT,
                    item(MenuItemId.RunAction(MenuActionId.COPY), "COPIAR"),
                    item(MenuItemId.RunAction(MenuActionId.PASTE), "PEGAR"),
                    item(MenuItemId.RunAction(MenuActionId.SELECT_ALL), "TODO"),
                    item(MenuItemId.RunAction(MenuActionId.CUT), "CORTAR"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.WEB,
                    item(MenuItemId.RunAction(MenuActionId.WEB_BACK), "ATRAS"),
                    item(MenuItemId.RunAction(MenuActionId.WEB_FORWARD), "ADELANTE"),
                    item(MenuItemId.RunAction(MenuActionId.SCROLL_UP), "SCROLL UP"),
                    item(MenuItemId.RunAction(MenuActionId.SCROLL_DOWN), "SCROLL DN"),
                    item(MenuItemId.RunAction(MenuActionId.NEW_TAB), "NUEVA T"),
                    item(MenuItemId.RunAction(MenuActionId.REFRESH), "RECARGAR"),
                    item(MenuItemId.RunAction(MenuActionId.CLOSE_TAB), "CERRAR T"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.MEDIA,
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_PLAY_PAUSE), "PLAY/PAUSE"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_NEXT), "SIGUIENTE"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_PREVIOUS), "ANTERIOR"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_FULLSCREEN), "FULLSCREEN"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_FORWARD_10_SECONDS), "ADELAN 10s"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_BACK_10_SECONDS), "ATRAS 10s"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.PLAY,
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_PLAY), "PLAY"),
                    item(MenuItemId.RunAction(MenuActionId.MEDIA_PAUSE), "PAUSE"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.VOLUME,
                    item(MenuItemId.RunAction(MenuActionId.VOLUME_UP), "SUBIR"),
                    item(MenuItemId.RunAction(MenuActionId.VOLUME_DOWN), "BAJAR"),
                    item(MenuItemId.RunAction(MenuActionId.VOLUME_MUTE), "MUTE"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
                menu(
                    MenuLevelId.NAV,
                    item(MenuItemId.RunAction(MenuActionId.SYSTEM_BACK), "ATRAS"),
                    item(MenuItemId.RunAction(MenuActionId.SYSTEM_HOME), "INICIO"),
                    item(MenuItemId.RunAction(MenuActionId.SYSTEM_RECENTS), "RECIENTES"),
                    item(MenuItemId.RunAction(MenuActionId.SYSTEM_NOTIFICATIONS), "NOTIF"),
                    item(MenuItemId.Back, "VOLVER"),
                ),
            ).associateBy { it.level },
        )

        private fun menu(
            level: MenuLevelId,
            vararg items: RadialMenuItem,
        ): RadialMenuDefinition = menu(level, MenuLevelId.PRINCIPAL, *items)

        private fun menu(
            level: MenuLevelId,
            parent: MenuLevelId?,
            vararg items: RadialMenuItem,
        ): RadialMenuDefinition = RadialMenuDefinition(level, parent, items.toList())

        private fun item(id: MenuItemId, label: String) = RadialMenuItem(id, label)
    }
}
