package com.example.system.dto;

import lombok.Getter;
import lombok.Setter;

/** Binding object for admin create/edit workstation (three E/L/Q slot rows + placement). */
@Getter
@Setter
public class WorkstationForm {

    private Long id;
    private String name;
    private Long placeAfterWorkstationId;

    private int engInputBit;
    private int engSlave = 2;
    private int engRelay = 1;
    private boolean clearEngAudio;
    private String currentEngAudioPath;
    /** Shown on edit form; not posted back for persistence. */
    private String currentEngAudioOriginalName;

    private int leadInputBit;
    private int leadSlave = 2;
    private int leadRelay = 1;
    private boolean clearLeadAudio;
    private String currentLeadAudioPath;
    private String currentLeadAudioOriginalName;

    private int qcInputBit;
    private int qcSlave = 2;
    private int qcRelay = 1;
    private boolean clearQcAudio;
    private String currentQcAudioPath;
    private String currentQcAudioOriginalName;
}
