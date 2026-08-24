package com.luics415.biogesture.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuCatalogTest {
    private val catalog = RadialMenuCatalog.DEFAULT

    @Test
    fun `catalog defines every typed level without placeholders`() {
        val definitions = catalog.allDefinitions()

        assertEquals(MenuLevelId.entries.toSet(), definitions.map { it.level }.toSet())
        assertTrue(definitions.all { it.items.isNotEmpty() })
        assertTrue(definitions.flatMap { it.items }.all { it.label.isNotBlank() })
        assertFalse(definitions.flatMap { it.items }.any { it.label.equals("NULL", ignoreCase = true) })
    }

    @Test
    fun `principal play entry opens the typed play submenu`() {
        val play = catalog.definition(MenuLevelId.PRINCIPAL).items.single { it.label == "PLAY" }
        assertEquals(MenuItemId.OpenLevel(MenuLevelId.PLAY), play.id)

        val playMenu = catalog.definition(MenuLevelId.PLAY)
        assertEquals(MenuLevelId.PRINCIPAL, playMenu.parent)
        assertTrue(playMenu.items.any { it.id == MenuItemId.RunAction(MenuActionId.MEDIA_PLAY) })
        assertTrue(playMenu.items.any { it.id == MenuItemId.RunAction(MenuActionId.MEDIA_PAUSE) })
    }

    @Test
    fun `principal back is an explicit close command`() {
        val back = catalog.definition(MenuLevelId.PRINCIPAL).items.single { it.label == "BACK" }
        assertEquals(MenuItemId.Close, back.id)
    }
}
