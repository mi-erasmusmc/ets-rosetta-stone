package eu.etransafe.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum MappingAlgorithm {

    ETOX2MEDDRAPT,

    HPATH2SEND,

    MA2SEND,
    MA2MEDDRASOC,
    MA2SNOMED,

    MEDDRAPT2ETOX,
    MEDDRAPT2SEND,
    MEDDRAPT2SNOMED,
    MEDDRAPT2MEDDRASOC,

    SEND2HPATH,
    SEND2MA,
    SEND2MEDDRAPT,

    SNOMED2MA
}
