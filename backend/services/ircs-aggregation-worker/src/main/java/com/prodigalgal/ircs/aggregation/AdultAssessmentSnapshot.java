package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
import java.util.UUID;

/** Immutable input plus optimistic fences for late responses and manual/content/source changes. */
record AdultAssessmentSnapshot(UUID id, String unifiedDigest, String sourceDigest, AdultAssessmentInput input) {}
