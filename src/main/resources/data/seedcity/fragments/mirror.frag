; an actuator does the opposite of a sensor for a while
SET $R1 $N1
$L1:
IN $R2 $S1
NOT $R2
OUT $A1 $R2
WAIT 1
SUB $R1 1
JZ $R1 $L2
JMP $L1
$L2:
