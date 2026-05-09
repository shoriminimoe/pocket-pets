package com.pocketpets.app.ui.pet

import com.pocketpets.app.R
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.ui.sprite.SpriteAnimation
import com.pocketpets.app.ui.sprite.SpriteSheet

/**
 * Maps each [CatState] to a [SpriteAnimation] on the bundled cat sprite sheet.
 *
 * Sheet layout (after the Phase 2 LPC swap, repacked by tools/fetch_cat_sprites.py):
 *  - row 0..3: walk S/N/W/E (4 frames each, 64x64 cells)
 *  - row 4:    sit (col 0)
 *  - row 5:    lay (col 0)
 *
 * The renderer's `facing` parameter adds row offsets 0/1/2/3 for SOUTH/NORTH/
 * WEST/EAST, so a single `walk` SpriteAnimation on row 0 becomes the
 * directional walk for free when AnimatedSprite is called with the right facing.
 */
object CatAnimations {
    private val sheet =
        SpriteSheet(
            resId = R.drawable.cat,
            frameWidth = 64,
            frameHeight = 64,
            rows = 6,
            cols = 4,
        )

    val walk: SpriteAnimation =
        SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 120, loop = true)
    val sit: SpriteAnimation = SpriteAnimation(sheet, row = 4, frameCount = 1)
    val lay: SpriteAnimation = SpriteAnimation(sheet, row = 5, frameCount = 1)

    fun forState(state: CatState): SpriteAnimation =
        when (state) {
            CatState.Walking -> walk
            CatState.Idle -> sit
            CatState.Lying -> lay
        }
}
