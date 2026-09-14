; ramp an actuator up to full in steps of three
SET $R1 0
$L1:
ADD $R1 3
OUT $A1 $R1
WAIT 1
SET $R2 $R1
NOT $R2
JZ $R2 $L2
JMP $L1
$L2:
