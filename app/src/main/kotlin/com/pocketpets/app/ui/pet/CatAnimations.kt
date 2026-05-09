package com.pocketpets.app.ui.pet

import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.ui.sprite.SpriteAnimation
import com.pocketpets.app.ui.sprite.SpriteSheet

/**
 * Maps each [Mood] to a [SpriteAnimation] on the bundled cat sprite sheet.
 *
 * The current asset (Surt's cat, repacked by tools/fetch_cat_sprites.py) is
 * a 64x128 sheet with two 64x64 cells stacked vertically:
 *
 *  - row 0: cat in a sitting pose
 *  - row 1: cat in a lying pose
 *
 * Each "animation" is a single static frame. Distinguishing the sit-based
 * moods from each other is delegated to [MoodOverlay], which draws particles
 * (heart / tear / squiggle / Z) on top of the sprite. The sense of life
 * comes from [PetScreen]'s Compose-layer breathing transform, not from
 * frame-by-frame animation.
 *
 * [GrowthStage] is accepted for future per-stage variation but currently
 * returns the same animation regardless of stage; visual sizing differs via
 * [PetScreen]'s sprite-Box dp size.
 */
object CatAnimations {
    private val sheet =
        SpriteSheet(
            resId = R.drawable.cat,
            frameWidth = 64,
            frameHeight = 64,
            rows = 2,
            cols = 1,
        )

    /** Static sit pose, sheet row 0. */
    val sit: SpriteAnimation =
        SpriteAnimation(
            sheet = sheet,
            row = 0,
            frameCount = 1,
        )

    /** Static lying-down pose, sheet row 1. */
    val lay: SpriteAnimation =
        SpriteAnimation(
            sheet = sheet,
            row = 1,
            frameCount = 1,
        )

    /**
     * Map [Mood] to the appropriate base animation. Sleeping cats lie down;
     * everyone else sits. Particle disambiguation lives in MoodOverlay.
     */
    fun forMood(
        stage: GrowthStage,
        mood: Mood,
    ): SpriteAnimation =
        when (mood) {
            Mood.SLEEPY -> lay
            Mood.IDLE,
            Mood.HAPPY,
            Mood.HUNGRY,
            Mood.GROSSED_OUT,
            Mood.SAD,
            -> sit
        }
}
