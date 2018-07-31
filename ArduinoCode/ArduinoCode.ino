
bool nokill = true;
byte steeringDeg, wheelSpeed;
bool wheelON, steerON;
int offf = 0;
int wheelDelay = 1500;
int steerDelay = 1500;
int normalDelay = 1000;

byte out[] = {0, 0, 0};
byte outsize = 3;

byte type;
byte pin;
byte value;

unsigned long lastNoKill = 0;
unsigned long sinceConnect = 0;
unsigned long sinceNoKill = 0; //Input timing for kill
unsigned long timeout = 30000; //microseconds before timeout
unsigned long lastTime = 0; //timekeeping for 50 hz, 20000 us reset
unsigned long lastRun = 0; //timekeeping for loop

//#define NOT_AN_INTERRUPT -1
//where 1ms is considered full left or full reverse, and 2ms is considered full forward or full right.

void setup() {
  // put your setup code here, to run once:
  //set pins to input/output
  pinMode(9, OUTPUT); //steering
  pinMode(10, OUTPUT); //speed
  pinMode(11, INPUT); //speed getter
  
  //pinMode(2, INPUT); //Dead man's switch
  
  pinMode(13, OUTPUT); //testing light
  digitalWrite(13, HIGH);

  Serial.begin(57600);
  Serial.setTimeout(1000); //Default value. available for change

  addMessage(1, 2, 6);
  
}

void addMessage(byte ina, byte inb, byte inc){
  out[outsize] = ina;
  out[outsize+1] = inb;
  out[outsize+2] = inc;
  outsize += 3;
}

void sendMessage(){
  if (outsize >= 3){
    byte msg[3] = {out[0], out[1], out[2]};
    Serial.write(msg, 3); //Send the first 3 items in the out list

    //Move values backwards in the list for the next run
    for (byte n = 0; n < outsize-3; n++){
      out[n] = out[n+3];
    }
    outsize -= 3;
  }
}


void loop() {
  lastRun = micros();

  //Read rise of signal
  if (sinceNoKill == 0 && digitalRead(2) == HIGH){
    sinceNoKill = micros();
  }

  //Read fall of signal
  if (sinceNoKill != 0 && digitalRead(2) == LOW){
    lastNoKill = micros();
    if (micros()-sinceNoKill < 1600){ //Check difference to find duration of input
      nokill = false;
      wheelDelay = 1500;
      steerDelay = 1500;
      //Send message to computer
      addMessage(4, 0, 3);
      sendMessage();
    }
    if (micros()-sinceNoKill > 1800){ //Start up if un-killed
      nokill = true;
    }
    sinceNoKill = 0;
  }

  if (micros()-lastNoKill > timeout){
    nokill = false;
    addMessage(4, 0, 4);
    sendMessage();
  }

  //Grab info from buffer
  if (Serial.available() > 0){
    digitalWrite(13, LOW);
    type = Serial.read();
    pin = Serial.read();
    value = Serial.read();
    sinceConnect = micros();

    if (pin == 9){
      if (value != steeringDeg){
        //steerDelay = 1.0+((double) value)/180;
        steerDelay = map(value, 0, 180, 1000, 2000);
      }
      steeringDeg = value;
    }
    
    if (pin == 10){
      if (value != wheelSpeed){
        //wheelDelay = 1.0+((double) value)/180;
        wheelDelay = map(value, 0, 180, 1000, 2000);
      }
      wheelSpeed = value;
    }
  
    if (type == 0xFF) { //Restart if a startup signal is recieved
      sinceConnect = micros();
      wheelDelay = 1500;
      steerDelay = 1500;
      nokill = true;
    }
  }
  
  //Start next cycle every .02 seconds
  if (micros()-lastTime >= 20000){
    
    digitalWrite(13, HIGH);
    digitalWrite(9, HIGH);
    digitalWrite(10, HIGH);
    steerON = true;
    wheelON = true;
    
    lastTime = micros();
  }

  //Turn off the signal at approximately the correct timing.
  if (steerON && micros()-lastTime >= steerDelay){
    digitalWrite(9, LOW);
    steerON = false;
  }

  if (wheelON && micros()-lastTime >= wheelDelay){
    digitalWrite(10, LOW);
    wheelON = false;
  }
  
  sendMessage();
  
  if (nokill){
    digitalWrite(13, HIGH);

    //Timeout check
    if (Serial.peek() <= 0 && micros()-sinceConnect > timeout){
      nokill = false;
      addMessage(5, 0, 4);
      sendMessage();
      digitalWrite(13, LOW);
    }

    //addMessage((byte) micros()-lastRun, 0, 0);

  } else {
    
    if (digitalRead(2) == HIGH) {
      digitalWrite(13, HIGH);
    } else {
      digitalWrite(13, LOW);
    }
  }
    
}

//jssc
//
