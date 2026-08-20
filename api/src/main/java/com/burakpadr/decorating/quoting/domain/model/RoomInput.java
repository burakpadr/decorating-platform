package com.burakpadr.decorating.quoting.domain.model;

/**
 * One room of the input (§5.1).
 *
 * <p>Stage 1 shape only: the type, which selects the room's coefficients, and the condition the
 * customer declared, which §5.6 turns into synthetic findings applied to every surface of the room.
 * Stage 2 adds per-surface findings and counted openings; that arrives with the stage 2 slice rather
 * than sitting here unread, so that nothing in the engine is driven by a field no test exercises.
 */
public record RoomInput(RoomType type, WallCondition declaredCondition) {}
