package com.pocketpets.app.domain.behavior

/**
 * Behavioural state of the cat. The state machine is implemented in
 * [CatBehaviorRules.tick]. Eating and Playing are duration-bounded by
 * [CatBehavior.stateUntil].
 */
enum class CatState { Idle, Walking, Lying, Eating, Playing }
