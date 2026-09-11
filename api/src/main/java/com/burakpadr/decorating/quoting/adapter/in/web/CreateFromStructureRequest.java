package com.burakpadr.decorating.quoting.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The only thing setup has to name to start a price book: what to call the version (BOYA-70).
 *
 * <p>Everything else comes from the shipped structure, and none of it is money. The figures arrive
 * afterwards, one screen at a time, through the endpoints that already exist.
 */
record CreateFromStructureRequest(@NotBlank @Size(max = 32) String versionCode) {}
