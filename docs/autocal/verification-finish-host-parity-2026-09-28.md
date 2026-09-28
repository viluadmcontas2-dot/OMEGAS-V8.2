# Finish AutoCal host-parity verification trigger

This file exists only to make the pull-request CI verify the exact OmegasVerde
source state that contains the ProgBase Finish AutoCal/Finish AutoMatch parity
work.

Authority under test:
- target branch: OmegasVerde
- source base SHA: a1a11ad1c229ee5f0e5e8e3780692614d8593ecb
- ProgBase binary SHA-256: 8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4

Critical invariant:
- read VECT_AUTOCAL_U8_1 (SerialCode 0x0165, RowIndex=1);
- write the same U8 value through VECT_AUTOCAL_U8_0, whose DFM has no RowIndex
  and whose TAebNumber setter dispatches through the scalar SetNumber path;
- scalar wire frame for value 0x06 is 12 65 01 06 7E;
- require ACK and readback before reporting Finish as confirmed.

No APK generation is authorized by this verification.
