/* TrakSim Car Simulator for use with NWAPW Year 3 Autonomous Car Project
 * Java Window App to Test/Demo TrakSim..
 *
 * This simulator pretends to be a camera using the FlyCamera API, and
 * watches the commands being sent to the Arduino through FakeFirmata,
 * and controls the simulated car based on those commands, then shows
 * what a forward-facing camera on the simulated car would see.
 *
 * TrakSim copyright 2018 Itty Bitty Computers and released at this time
 * to the public as open source. There are no warranties of any kind.
 *
 * FakeFirmata is designed to work with JSSC (Java Simple Serial Connector),
 * but if you are developing your self-driving code on some computer other
 * than LattePanda, you can substitute package noJSSC, which has the same
 * APIs (as used by FakeFirmata) but does nothing.
 */
package com.apw.drivedemo;                                    // 2018 June 13

import com.apw.Steering.SteeringBase;
import com.apw.Steering.SteeringMk1;
import com.apw.Steering.SteeringMk2;
import com.apw.apw3.*;
import com.apw.fakefirm.Arduino;
import com.apw.fly2cam.FlyCamera;
import com.apw.Steering.Point;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// import fly2cam.CameraBase;

public class BrBemo extends JFrame implements MouseListener {
    private double sumOfAngles = 0;
    private double locX = 0;
    private double locY = 0;

    private int frameCount = 0;
    private boolean drawnMap = false;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private static final long serialVersionUID = 1L; // unneed but Java insists {

    private static final int MinESCact = DriverCons.D_MinESCact,
            MaxESCact = DriverCons.D_MaxESCact, StartGas = MinESCact*9/4,
            ScrPix = TrakSim.nPixels, nServoTests = DriverCons.D_nServoTests,
            ScrHi = DriverCons.D_ImHi, ImgWi = DriverCons.D_ImWi, Haf_Scrn = ImgWi/2,
            ScrTop = ScrHi-1-DriverCons.D_DrawDash, SimTile = DriverCons.D_BayTile,
            FastSet = (MaxESCact+MinESCact)/2,
            ScrWi = TrakSim.WinWi,
            ScrFrTime = DriverCons.D_FrameTime, Scr2L = ScrWi*2,
            CamFPS = FlyCamera.FrameRate_15-1, // CamTime = 528>>CamFPS, // cam frame in ms

    DrawDash = DriverCons.D_DrawDash, CarColo = DriverCons.D_CarColo,
            AddColo = DriverCons.D_MarinBlue,
            SteerPin = DriverCons.D_SteerServo, GasPin = DriverCons.D_GasServo;

    private static final double LefScaleSt = ((double)DriverCons.D_LeftSteer)/90.0,
            RitScaleSt = ((double)DriverCons.D_RiteSteer)/90.0;

    private static final boolean StartInCalibrate = DriverCons.D_StartInCalibrate,
            ContinuousMode = false, DrawStuff = true, LiveCam = DriverCons.D_LiveCam,
            StartLive = DriverCons.D_StartLive, ShowMap = DriverCons.D_ShowMap,
            ShoClikGrid = DriverCons.D_ShoClikGrid;

    private static int StartYourEngines = 0, NoisyFrame = 999, // (35*4096+34)*4096+33,
            ServoTstPos = 0, ServoTestCount = 0, // remaining number of steps
            NoneStep = 0, // >0: pause simulation after each recalc
            ViDied = 0, CamTile = 0, CamTall = 0, CamWide = 0, CamFrame = 0;

    private FlyCamera theVideo = null;
    private FlyCamera simVideo = null;
    private Arduino theServos = null;
    private TrakSim theSim = null;
    private byte[] CamPix = null;

    private SteeringBase testSteering = null;

    private boolean StepMe = false, SimSpedFixt = DriverCons.D_FixedSpeed,
            CamActive = false, beenThrough = false;

    private static JFrame theWindow = null; // (used by static method)

    public static class RunSoon implements Runnable { @Override
    public void run() {starting();}} //~RunSoon

    private Timer TickTock = null;
    private BufferedImage theImag = null;
    private BufferedImage theBuff = null;
    private byte[] SimBytes = null;
    public int[] thePixels = null;


    private int Calibrating = 0;
    private int SteerDegs = 0;
    private int GasPedal = 0;
    public int DidFrame = 0;
    public int DarkState = 0;
    private boolean OhDark = false;
    private boolean BusyPaint = false;
    private boolean CanDraw = false;
    public boolean CameraView = false;
    public boolean unPaused = false;

    private final int[] Grid_Moves = {0,-32,-8,-1,0,1,8,32, 0,1,8,32, 0,1,8,32};

    private class PaintAction implements ActionListener { @Override
    public void actionPerformed(ActionEvent evt) {
        if (theWindow != null) theWindow.repaint();}} //~PaintAction

    private PaintAction doOften = null;


    /**
     * Gets the next image from whichever camera is selected.
     *
     * @return       True if successful, false otherwise.
     *               The image is returned in class variable thePixels
     */
    public boolean GetCameraImg() { // -> thePixels, true if OK // (in DrDemo)
        int rx, cx, wide = ScrWi, my2L = Scr2L, // Scr2L = WinWi*2
                tile = 0, cz = 0, zx = 0, whom = 0, here = 0, thar = 0;
        boolean alive = LiveCam&&CameraView&&CamActive&&(CamPix != null),
                gotit = alive;
        byte[] myBy = SimBytes; // local vars visible in debugger ;-)
        int[] myPix = thePixels; // both are dim'd ScrPix = ImHi*WinWi
        FlyCamera myVid = simVideo;
        OhDark = true;
        if (alive) {
            myVid = theVideo;
            tile = CamTile;
            wide = CamWide;
            my2L = wide+wide;
            myBy = CamPix;} //~if
        else tile = SimTile;
        if (myBy != null) if (myPix != null) if (myVid != null) {
            if (!alive) if (!ContinuousMode) if (StepMe) if (NoneStep >= 0)
                if (Calibrating==0) {
                    theSim.SimStep(1);
                    NoneStep = -NoneStep;} //~if
            StepMe = false;
            gotit = myVid.NextFrame(myBy);
            if (alive) if (!gotit) {
                if (myVid.Live()) whom++;
                whom++;}} //~if
        if (tile != 1) if (tile != 3) gotit = false;
        if (!gotit) {
            ViDied++;
            System.out.println(HandyOps.TF2Log("** GetCamIm ",(myVid != null),
                    HandyOps.TF2Log("/",myBy != null,HandyOps.TF2Log("/",myPix != null,
                            HandyOps.TF2Log(" = ",gotit,HandyOps.Dec2Log(" ** ",wide, // gotit =
                                    HandyOps.Dec2Log("/",ScrWi,HandyOps.TF2Log(" ",alive,
                                            HandyOps.Dec2Log("/",whom,HandyOps.Dec2Log(" ",tile,
                                                    HandyOps.Dec2Log(" ",ViDied,""))))) ))))) );
            if (whom !=0) if (myVid != null) System.out.println(myVid.toString());
            return false;} //~if
        if (!alive) CamFrame = theSim.GetFrameNo(); // updated by GetSimFrame
        else CamFrame ++;
        for (rx=0; rx <= ScrHi-1; rx++) {
            cz = wide-1;
            for (cx=0; cx <= cz; cx++) {
                if (myBy == null) break; // can't, Java throws an exception instead
                zx = here+my2L;
                if (zx<0) break;
                if (zx>myBy.length -2) break;
                if (here<0) break;
                if (here>myBy.length -2) break;
                if (tile==1) whom = (( ((((int)myBy[here])&255)<<8)        // RG/GB..
                        | ((int)myBy[here+1])&255)<<8)|((int)myBy[zx+1])&255;
                else if (tile==3) whom = (( ((((int)myBy[zx])&255)<<8)   // GB/RG..
                        | ((int)myBy[here])&255)<<8)|((int)myBy[here+1])&255;
                here = here+2;
                if (myPix == null) break;
                if (thar<0) break;
                if (thar >= myPix.length) break;
                myPix[thar] = whom; // <- unsaturated result
                if ((whom&0x00F0F0F0) !=0) OhDark = false;
                thar++;} //~for // (cx)
            here = here+my2L;
            thar = thar+ScrWi-wide;} //~for // (rx)
        return true;} //~GetCameraImg

    /**
     * Sends a steering servo message to the hardware (and to TrakSim).
     *
     * @param  fixt  True: whar is a signed absolute angle (usually 0);
     *               False: whar is a signed inc/decrement to current setting
     *
     * @param  whar  The angle (increment) for the steering servo
     */
    public void SteerMe(boolean fixt, int whar) { // -> SetServo // SteerServo=9
        if (!fixt) whar = SteerDegs+whar; // SteerDeg is centered on 0
        whar = MyMath.iMax(MyMath.iMin(whar,90),-90);
        if (whar !=0) if (whar==SteerDegs) return;
        SteerDegs = whar;
        if (Calibrating==0) {
            if (whar<0) {
                if (LefScaleSt<1.0) // LefScaleSt = LeftSteer/90.0
                    whar = (int)Math.round(LefScaleSt*((double)whar));} //~if
            else if (whar>0) if (RitScaleSt>1.0)
                whar = (int)Math.round(RitScaleSt*((double)whar));} //~if
        if (theServos == null) return;
        StepMe = true;
        theServos.servoWrite(SteerPin,whar+90);} //~SteerMe

    /**
     * Sends a drive ESC message to the hardware (and to TrakSim).
     *
     * @param  fixt  True: whar is a signed absolute velocity;
     *               False: whar is a signed inc/decrement to current setting
     *
     * @param  whar  The velocity (increment) for the ESC
     */
    public void AxLR8(boolean fixt, int whar) { // -> SetServo // GasServo=10
        if (!fixt) whar = GasPedal+whar; // GasPed is centered on 0
        if (whar !=0) {
            whar = MyMath.iMax(MyMath.iMin(whar,90),-90);
            if (whar==GasPedal) return;} //~if
        if (Calibrating==0) if (whar==0) if (!fixt)
            if (StartYourEngines==0) if (GasPedal==0) return;
        GasPedal = whar;
        if (theServos == null) return;
        StepMe = true;
        theServos.servoWrite(GasPin,whar+90);} //~AxLR8

    /**
     * Safe shutdown, because some hardware drivers need their close.
     *
     * @param  why   A number to log, the reason or cause
     */
    private void Stopit(int why) { // gotta turn the camera & JSSC off..
        FlyCamera myVid = theVideo;
        try {
            AxLR8(true,0);
            SteerMe(true,0);
            if (myVid != null) myVid.Finish();
            if (theServos != null) theServos.Close();
        } catch (Exception ex) {}
        System.out.println("-------- Clean Stop -------- " + why);
        System.exit(why);}


    // Java wants these if 'implements MouseListen' ..
    @Override public void mouseExited(MouseEvent evt) {}
    @Override public void mousePressed(MouseEvent evt) {}
    @Override public void mouseReleased(MouseEvent evt) {}

    /**
     * Recognize a mouse rollover into the top left corner of the screen from
     *   outside the window, so to start up self-driving software (or whatever).
     */
    @Override public void mouseEntered(MouseEvent evt) { // (in DrDemo)
        Insets edges = getInsets();
        int nx = 0, Vx = 0, Hx = 0, why = 0;
        while (true) {
            why++; // why = 1
            if (!StartLive) return; // don't even log
            why++; // why = 2
            if (Calibrating !=0) break;
            why++; // why = 3
            if (!CameraView) break;
            if (evt != null) if (edges != null) {
                Hx = evt.getX()-edges.left;
                Vx = evt.getY()-edges.top;} //~if
            why++; // why = 4
            if (Hx>ImgWi) break;
            nx = theSim.GridBlock(Vx,Hx); // find which screen chunk it came in..
            why++; // why = 5
            if (nx != 0x10001) break; // top left corner only (from outside win)..
            NoneStep = 0; // continuous
            theSim.SimStep(0);
            unPaused = true; // start it running..
            why++; // why = 6
            if (StartYourEngines>0) break;
            if (ContinuousMode) theSim.SimStep(2);
            StartYourEngines++;
            DidFrame = 0;
            if (SimSpedFixt) AxLR8(true,StartGas);
            else if (DarkState<2) DarkState = 2;
            // You can use (DarkState >= 2) to start your self-driving software,
            // ..or else insert code here to do that..
            why = 0;
            break;} //~while
        System.out.println(HandyOps.Dec2Log("(DrDemo) Got MousEnt = ",why,
                HandyOps.Dec2Log(" @ ",Vx,HandyOps.Dec2Log(",",Hx,
                        HandyOps.Int2Log(": ",nx,
                                HandyOps.Dec2Log(" ",StartYourEngines,
                                        HandyOps.TF2Log(" g=",unPaused,HandyOps.TF2Log(" cv=",CameraView,
                                                HandyOps.Dec2Log(" ns=",NoneStep,HandyOps.Dec2Log(" ",Calibrating,
                                                        HandyOps.PosTime( ((
                                                                " @ "))))) ))))) )));} //~mouseEntered

    /**
     * Accepts clicks on screen image to control operation
     */
    @Override
    public void mouseClicked(MouseEvent evt) { // (in DrDemo)
        Insets edges = getInsets();
        int kx = 0, nx = 0, zx = 0, Vx = 0, Hx = 0, why = 0;
        boolean didit = false, rite = false; /// ***
        if (evt != null) if (edges != null) { // we only implement/o'ride this one
            if (evt.getButton() == MouseEvent.BUTTON3) rite = true; /// ***
            Hx = evt.getX() - edges.left;
            Vx = evt.getY() - edges.top;
        } //~if
        if (Hx < ImgWi) {
            why = theSim.GridBlock(Vx, Hx); // find which screen chunk it's in..
            zx = why & 0xFF;
            nx = why >> 16;
            if (nx < 3) { // top half, switch to camera view..
                didit = ((nx | zx) == 1); // top left corner simulates covering lens..
                if (didit) theSim.DarkFlash(); // unseen if switches to live cam
                CameraView = (theVideo != null)
                        && (CamPix != null);
                if (CameraView) {
                    theSim.SimStep(0);
                    if (Calibrating > 0) SteerMe(true, 0);
                    else if (Calibrating < 0) AxLR8(true, 0); // stop
                    else if (didit) { // if click top-left, stop so ESC can recover..
                        AxLR8(true, 0);
                        unPaused = false;
                        StartYourEngines = 0;
                    } //~if
                    Calibrating = 0;
                } //~if
                DidFrame = 0;
                unPaused = CameraView && (nx == 1);
            } //~if // top edge runs // (nx<3)
            else if (nx == 3) { // middle region, manual steer/calibrate..
                if (Calibrating < 0) {
                    if (zx == 4) AxLR8(true, 0);
                    else AxLR8(false, Grid_Moves[zx & 7]);
                } //~if
                else if (zx == 4) SteerMe(true, 0);  // Grid_Moves={0,-32,-8,-1,0,1,8,32,..
                else SteerMe(false, Grid_Moves[zx & 7]);
                theSim.FreshImage();
            } //~if
            else if (Calibrating > 0) {
                SteerMe(true, 0); // straight ahead
                Calibrating = -1;
            } //~if
            else if (Calibrating < 0) {
                AxLR8(true, 0); // stop
                Calibrating = 0;
                theSim.SimStep(1);
                StartYourEngines = 0;
            } //~if
            else if (nx == 5) { // bottom, switch to sim view..
                CameraView = false;
                DidFrame = 0;
                if (theSim.IsCrashed()) theSim.SimStep(0); // clear crashed mode
                if (zx == 2) NoneStep = 1; // left half: 1-step
                else NoneStep = 0; // right half: continuous
                if (ContinuousMode) theSim.SimStep(2);
                else theSim.SimStep(1);
                unPaused = ((zx > 1) && (zx < 4));
            } //~if // corners: DrDemo not control speed
            else if (nx == 4) { // low half..
                if (zx < 2) Stopit(0); // low half, left edge, kill it politely
                else if (!CameraView) // otherwise toggle pause..
                    unPaused = !unPaused;
            }
        } //~if
        else if (ShowMap) {
            zx = theSim.GetMapSize(); // MapHy,MapWy = size of full map
            nx = Hx - 2 - ImgWi;
            if ((Vx < (zx >> 16)) && (nx < (zx & 0xFFF)))
                theSim.SetStart(Vx, nx, MyMath.Trunc8(theSim.GetFacing()));
            else zx = theSim.ZoomMap2true(!rite, Vx, Hx); // sets facing to -> click
            if (rite) { /// ***
                Vx = zx >> 22; // ZoomMap2tru rtns (r,c)<<6..
                Hx = (zx >> 6) & 0xFF;
                if (Vx > 0) if (Hx > 0) theSim.SetStart(Vx, Hx, MyMath.Trunc8(theSim.GetFacing()));
            } /// ***
            unPaused = false; // pause if click on map
            theSim.FreshImage();
        } //~if
        if (Calibrating == 0) {
            why = 256;
            if (!unPaused) { // pause it..
                why--; // why = 255
                if (StartYourEngines > 0) theSim.SimStep(0);
                StartYourEngines = 0;
                AxLR8(true, 0);
            } //~if
            else if (StartYourEngines == 0) { // start..
                why++; // why = 257
                if (ContinuousMode) theSim.SimStep(2);
                // else theSim.SimStep(1);
                StartYourEngines++;
                DidFrame = 0;
                if (SimSpedFixt && (ServoTestCount == 0)) AxLR8(true, StartGas);
                else if (DarkState < 2) DarkState = 2;
            }
        } //~if
        System.out.println(HandyOps.Dec2Log("(DrDemo) Got click @ ", Vx,
                HandyOps.Dec2Log(",", Hx, HandyOps.Dec2Log(": ", nx,
                        HandyOps.Dec2Log("/", zx, HandyOps.Dec2Log(" +", kx,
                                HandyOps.Dec2Log(" ", StartYourEngines,
                                        HandyOps.TF2Log(" s=", true,
                                                HandyOps.TF2Log(" g=", unPaused, HandyOps.TF2Log(" cv=", CameraView,
                                                        HandyOps.Dec2Log(" ns=", NoneStep, HandyOps.Dec2Log(" ", Calibrating,
                                                                HandyOps.Dec2Log(" ", why,
                                                                        HandyOps.PosTime((
                                                                                " @ ")))))))))))))));
    } //~mouseClicked

    /**
     * Exercise steering & ESC servos, but only if live camera.
     */
    public void TestServos() { // exercise steering & ESC servos
        final int ServoMsgPos = DriverCons.D_ServoMsgPos,
                LeftSteer = DriverCons.D_LeftSteer,
                RiteSteer = DriverCons.D_RiteSteer,
                ServoMsgTL = DriverCons.D_ServoMsgTL,
                ServoMsgSiz = DriverCons.D_ServoMsgSiz;
        boolean nowate = (nServoTests>99)&&((nServoTests&1) !=0),
                doit = (StartYourEngines !=0) || nowate, flash = doit;
        int faze = CamFrame, info = ScrFrTime, why = 0;
        if (nServoTests <= 0) return; // normal exit (test turned off)
        if (info <= 0) return; // shouldn't happen
        while (info<400) { // reduce this to half-second steps..
            if ((faze&1) !=0) return; // ignore intermediate frames
            faze = faze>>1;
            info = info<<1;} //~while
        while (true) {
            why++; // why = 1
            if (ServoTestCount <= 0) break;
            why++; // why = 2
            if (!CamActive) break; // only works if camera is turned on
            why++; // why = 3
            if (StartLive) break; // only if manual start
            why++; // why = 4
            if (!CanDraw) break;
            why++; // why = 5
            if (ServoMsgPos <= 0) break; // position of warning in image file,
            why++; // why = 6
            if (ServoMsgTL <= 0) break;
            why++; // why = 7
            if (ServoMsgSiz <= 0) break;
            why++; // why = 8
            if (ServoTstPos==0) {
                ServoTstPos = theSim.GetImgWide();
                if (ServoTstPos <= 0) break;
                ServoTstPos = (ServoMsgPos>>16)*ServoTstPos+(ServoMsgPos&0xFFFF);} //~if
            why++; // why = 9
            if (flash) if ((ServoTestCount&1)==0) flash = false;
            if (!flash) theSim.SeeOnScrnPaint(ServoMsgTL>>16,ServoMsgTL&0xFFFF,
                    ServoMsgSiz>>16,ServoMsgSiz&0xFFFF,ServoTstPos,-1);
            if (!CameraView) break;
            why++; // why = 9
            if (!doit) break;
            why = 0;
            ServoTestCount--;
            faze = ServoTestCount&7;
            switch (faze) {
                case 0:
                case 2:
                case 4:
                    AxLR8(true,0);
                    SteerMe(true,0);
                    break;
                case 1:
                    if (LeftSteer>90) why = 11;
                    else if (LeftSteer<0) why = 11;
                    else SteerMe(true,-LeftSteer);
                    break;
                case 3:
                    if (RiteSteer>90) why = 13;
                    else if (RiteSteer<0) why = 13;
                    else SteerMe(true,RiteSteer);
                    break;
                case 5:
                    if (MaxESCact>90) why = 15; // finally fast
                    else if (MaxESCact<0) why = 15;
                    else AxLR8(true,MaxESCact);
                    break;
                case 6:
                    if (FastSet>90) why = 16; // then medium
                    else if (FastSet<0) why = 16;
                    else AxLR8(true,FastSet);
                    break;
                case 7:
                    if (MinESCact>90) why = 17; // first slow
                    else if (MinESCact<0) why = 17;
                    else AxLR8(true,MinESCact);
                    break;} //~switch
            break;} //~while
        System.out.println(HandyOps.Dec2Log("(TestServo) ",faze,
                HandyOps.Dec2Log("/",CamFrame,
                        HandyOps.Dec2Log(" = ",why,""))));} //~TestServos

    /**
     * Examples of drawing on TrakSim image. Delete what you don't need...
     */
    public void DrawDemo() { // examples of drawing on TrakSim image..
        int haff = ScrHi/2, tx = theSim.TurnRadRow(), t2x = tx>>16,
                HafScrn = ImgWi/2, nx, zx, whar = 0;
        double fmid = MyMath.Fix2flt(Haf_Scrn,0);
        String aLine;
        if (!CanDraw) return;
        tx = tx&0xFFF;
        for (nx=ScrHi-8; nx >= 8; nx += -8) { // draw tics on right edge of image..
            if ((nx&31)==0) zx = ImgWi-5;
            else zx = ImgWi-2;
            theSim.DrawLine(AddColo,nx,zx,nx,ImgWi-1);} //~for
        if (Calibrating !=0) {
            theSim.SetPixSize(4);
            if (Calibrating>0) zx = SteerDegs;
            else zx = GasPedal;
            if (zx>9) nx = 16;
            else if (zx < -9) nx = 24;
            else if (zx<0) nx = 16;
            else nx = 8;
            theSim.LabelScene(HandyOps.Dec2Log("",zx,""),haff-80,HafScrn+nx,0);
            if (Calibrating>0) aLine = "S";
            else aLine = "E";
            theSim.LabelScene(aLine,haff-12,HafScrn+8,AddColo);
            if (Calibrating>0) aLine = "-E-";
            else aLine = "-0-";
            theSim.LabelScene(aLine,haff+40,HafScrn+23,CarColo);
            theSim.SetPixSize(0);} //~if
        else if (tx>haff) { // draw pink lines at turn radius tx & 2*tx = t2x..
            if (tx<ScrTop) theSim.DrawLine(CarColo,tx,Haf_Scrn-32,tx,Haf_Scrn+32);
            else tx = ScrTop-1;
            if (t2x>haff) if (t2x<tx) { // also center line between them..
                //center
                theSim.DrawLine(CarColo,t2x,Haf_Scrn,tx,Haf_Scrn);
                //top
                theSim.DrawLine(CarColo,t2x,Haf_Scrn-32,t2x,Haf_Scrn+32);
                whar = theSim.ZoomMapCoord(false,MyMath.Fix2flt(tx,0),fmid);
                if (whar>0) { // draw center line on map close-up, if possible..
                    zx = 0;
                    while (t2x+4<tx) {
                        zx = theSim.ZoomMapCoord(false,MyMath.Fix2flt(t2x,0),fmid);
                        if (zx>0) break;
                        t2x = t2x+4;} //~while
                    if (zx>0) theSim.DrawLine(CarColo,zx>>16,zx&0xFFF,
                            whar>>16,whar&0xFFF);}}}} //~DrawDemo

    /**
     * Converts a RGB pixel array to BufferedImage for painting.
     * Adapted from example code found on StackExchange.
     *
     * @param  pixels  The pixel array
     *
     * @param  width   Its width
     *
     * @param  height  Its height
     *
     * @return         The BufferedImage result
     */
    public BufferedImage Int2BufImg(int[] pixels, int width, int height) // (in DrDemo)
            throws IllegalArgumentException {
        int lxx = 0;
        int[] theData = null; // Raster raz = null;  DataBufferInt DBI = nell;
        BufferedImage bufIm = null;
        if (pixels != null) lxx = pixels.length;
        if (lxx==0) return null;
        if (width==ScrWi) if (height==ScrHi) bufIm = theBuff;
        if (bufIm==null) // (should be never)
            bufIm = new BufferedImage(width,height, BufferedImage.TYPE_INT_RGB);
        if (bufIm==null) return null;
        theData = ((DataBufferInt) bufIm.
                getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels,0,theData,0,lxx);
        return bufIm;} //~Int2BufImg

    /**
     * All the heavy lifting happens here. Called on timer activation.
     */
    @Override public void paint(Graphics graf) { // (in DrDemo)
        int nx, fno = CamFrame, DimSave = 0;
        int[] SaveScrn = null;
        Insets edges;
        if (DriverCons.D_Log_Draw)
            System.out.println(HandyOps.TF2Log("DrDemo.paint ", BusyPaint,
                    HandyOps.TF2Log(" ", theImag != null,
                            HandyOps.TF2Log(" ", thePixels != null, ""))));
        if (BusyPaint) return; // Java won't accept local static method vars
        if (ViDied > 3) Stopit(ViDied); // video gone
        BusyPaint = true;
        edges = getInsets();
        super.paint(graf);
        // if (theImag == null)
        if (thePixels != null) try {
            if (theImag != null) if (graf != null) if (edges != null)
                graf.drawImage(theImag, edges.left, edges.top, null);
            if (StartYourEngines > 0) StepMe = true;
            if (GetCameraImg()) {
                if (NoisyFrame != 0) if ((NoisyFrame & 0xFFF) < CamFrame)
                    NoisyFrame = (NoisyFrame >> 12) & 0xFFFFF;
                // Use: if ((NoisyFrame&0xFFF)==CamFrame) ...
                fno = CamFrame;
                if (((DarkState & 1) == 0) == OhDark) { // if (CamActive)
                    DarkState++; // briefly cover camera lens for state change..
                    System.out.println(HandyOps.Dec2Log("*** DarkFrame ", fno,
                            HandyOps.Dec2Log(" ", DarkState, HandyOps.PosTime(" *** @ "))));
                } //~if
                CanDraw = false;
                if (DrawStuff) {
                    DimSave = theSim.GetMyScreenDims();
                    SaveScrn = theSim.GetMyScreenAry();
                    if (thePixels != null) {
                        theSim.SetMyScreen(thePixels, ScrHi, ScrWi, 1);
                        if (DimSave == 0) DimSave--;
                        CanDraw = true;
                        if (!ShoClikGrid) { // otherwise TrakSim did it..
                            nx = 0;
                            if (Calibrating != 0) nx++;
                            else nx++;
                            if (nx > 0) theSim.DrawGrid();
                        }
                    } //~if
                    else DimSave = 0;
                } //~if
                if ((StartYourEngines == 0) || (NoneStep < 0)) fno = DidFrame;

                ///** If you have self-driving code, you could put it here **///
                TestServos(); // (replace this with your own code)
                if (CanDraw) {
                    DrawDemo();
                    if (CameraView) theSim.DrawSteerWheel(SteerDegs, true, true);
                } //~if
                DidFrame = fno;
                ///? if (!CameraView) if (StartYourEngines>0) {
                ///?   theSim.SimStep(0);
                ///?   StartYourEngines = 0;
                ///?   AxLR8(true,0);} //~if
                if (DrawStuff) if (CanDraw) if (DimSave > 0) if (SaveScrn != null)
                    theSim.SetMyScreen(SaveScrn, DimSave >> 16, DimSave & 0xFFFF, 1);
                theImag = Int2BufImg(thePixels, ScrWi, ScrHi);
            }
        } //~if
        catch (Exception ex) {
            theImag = null;
        }
        BusyPaint = false;

        this.GetCameraImg();


        // Steer between lines
        if (DriverCons.D_steeringVersion == 1) {
            theServos.servoWrite(SteerPin, ((testSteering.drive(thePixels)) + 90));
        } else {
            testSteering.findPoints(thePixels);
        }
        int tempDeg = testSteering.drive(thePixels);
        testSteering.findPoints(thePixels);

        if (StartYourEngines > 0) {
    	/*
    		if (Math.abs(tempDeg - prevAngle) < 6 && onTurn == false) {
			onTurn = true;
    		}
		if (Math.abs(tempDeg - prevAngle) > 6 && onTurn == true) {
			onTurn = false;

    		}
		if (onTurn == false) {
		*/
            // 	(speed * time) / radius
            double inRadiusAngle = .57 / 2 * (double) tempDeg;
            double outRadiusAngle = .38 / 2 * (double) tempDeg;
            if (tempDeg < 0) {
                outRadiusAngle = .45 / 2 * tempDeg;
                inRadiusAngle = 0.7 / 2 * (double) tempDeg;
            }

            double turnRadiusIn = 2.68 / Math.tan(Math.toRadians(tempDeg * .37)) + .5 * (1.976);
            double turnRadiusOut = 2.68 / Math.tan(Math.toRadians(tempDeg * .37)) - .5 * (1.976);
            double averageTurnRadius = (turnRadiusIn + turnRadiusOut) / 2;
            double angleTurned = ((double) DriverCons.D_FrameTime / 1000.0) * DriverCons.D_fMinSpeed / averageTurnRadius * 2;

            angleTurned = Math.toDegrees(angleTurned);
            if (tempDeg == 0) angleTurned = 0;

            sumOfAngles += (double) angleTurned;
            sumOfAngles = sumOfAngles;

            //if (sumOfAngles > 360) sumOfAngles = sumOfAngles - 360;
            //if (sumOfAngles < 0) sumOfAngles = sumOfAngles + 360;
            //}

            locX = locX + (double) Math.cos(Math.toRadians(sumOfAngles)) * (double) DriverCons.D_FrameTime / 1000 * (double) DriverCons.D_fMinSpeed;
            locY = locY + (double) Math.sin(Math.toRadians(sumOfAngles)) * (double) DriverCons.D_FrameTime / 1000 * (double) DriverCons.D_fMinSpeed;


            // Draw lines on road
            graf.setColor(Color.RED);
            //graf.fillRect(100, testSteering.startingPoint, 1, 1);

            if (DriverCons.D_DrawPredicted) {
                int tempY = 0;
                for (int idx = 0; idx < testSteering.midPoints.size(); idx++) {
                    if (idx >= testSteering.startTarget && idx <= testSteering.endTarget) {
                        graf.setColor(Color.red);
                        tempY += testSteering.midPoints.get(idx).y;
                        graf.fillRect(testSteering.midPoints.get(idx).x, testSteering.midPoints.get(idx).y + edges.top, 5, 5);
                    } else {
                        graf.setColor(Color.BLUE);
                    }
                    // graf.fillRect(testSteering.midPoints.get(idx).x, testSteering.midPoints.get(idx).y + edges.top, 5, 5);
                }
                System.out.println(tempY / (1.0 * (testSteering.endTarget - testSteering.startTarget)));
            }

            if (DriverCons.D_DrawOnSides) {
                for (Point point : testSteering.leftPoints) {
                    graf.setColor(Color.YELLOW);
                    graf.fillRect(point.x + edges.left, point.y + edges.top, 5, 5);
                }
                for (Point point : testSteering.rightPoints) {
                    graf.fillRect(point.x + edges.left, point.y + edges.top, 5, 5);
                }
            }


            // Draw steerPoint on screen
            graf.setColor(Color.CYAN);
            graf.fillRect(testSteering.steerPoint.x, testSteering.steerPoint.y, 7, 7);


            //Draw predicted points and detected lines
            for (Point point : testSteering.midPoints) {
                if (DriverCons.D_DrawPredicted) {
                    this.theSim.RectFill(255, point.y, point.x, point.y + 5, point.x + 5);
                }
            }
            if (DriverCons.D_DrawOnSides) {
                for (Point point : testSteering.leftPoints) {
                    int xL = point.x;
                    int yL = point.y;
                    this.theSim.RectFill(16776960, yL, xL, yL + 5, xL + 5);
                }
                for (Point point : testSteering.rightPoints) {
                    int xR = point.x;
                    int yR = point.y;
                    this.theSim.RectFill(16776960, yR, xR, yR + 5, xR + 5);
                }
            }


        }
    }//~paint

    private static void starting() {theWindow = new DrDemo();}

    public static void main(String[] args) { // (in DrDemo)
        Runnable runFrameLater = new RunSoon();
        System.out.println(HandyOps.Dec2Log("(main) image size ",ScrWi,
                HandyOps.Dec2Log("x",ScrHi,HandyOps.Dec2Log(" = ",ScrPix,""))));
        SwingUtilities.invokeLater(runFrameLater);}

    /**
     * This is the constructor, which sets everything up.
     */
    public BrBemo() { // outer class constructor..
        int nx = ScrPix; // CamFPS = FlyCamera.FrameRate_15-1;
        String sayso = "= " + ScrWi + "x" + ScrHi; // "(Cal8="
        boolean dunit = false;
        Timer titok;
        FlyCamera myVid = null;
        int[] myPix;
        unPaused = !StartLive; // F=paused, so it requires user action to start
        if (StartInCalibrate) Calibrating = 1;
        else if (LiveCam) if (nServoTests>0) if (ScrFrTime>40)
            if (ScrFrTime<555) ServoTestCount = nServoTests*8-4;
        System.out.println(HandyOps.Dec2Log("(Cal8=",Calibrating,
                HandyOps.Dec2Log(") pix ",ScrPix*4,sayso)));
        simVideo = new SimCamera();
        theServos = new Arduino();
        theSim = new TrakSim();
        if (DriverCons.D_steeringVersion == 1) {
            testSteering = new SteeringMk1();
        } else if (DriverCons.D_steeringVersion == 2) {
            testSteering = new SteeringMk2(SteerPin, theServos);
        }
        if (LiveCam) theVideo = new FlyCamera();
        ViDied = 0;
        dunit = theServos.IsOpen();
        myPix = new int[ScrPix]; // ScrPix = ImHi*WinWi
        SimBytes = new byte[ScrPix*4];
        thePixels = myPix;
        while (nx>0) {
            if (myPix==null) break;
            nx--;
            if (nx<0) break;
            if (nx<myPix.length) myPix[nx] = 0x6699CC;} //~while // prefill with blue-gray
        doOften = new PaintAction();
        TickTock = new Timer(
                ScrFrTime,doOften);
        // reduce memory-manager burden by pre-allocating this..
        theBuff = new BufferedImage(ScrWi,ScrHi, BufferedImage.TYPE_INT_RGB);
        titok = TickTock;
        myVid = simVideo;
        if (!dunit) System.out.println("FakeFirmata failed to open "
                + Arduino.CommPortNo);
        else if (myVid==null) dunit = false;
        else try {
                dunit = myVid.Connect(CamFPS); // SteerPin = DriverCons.D_SteerServo = 9..
                theServos.pinMode(SteerPin, Arduino.SERVO);
                theServos.pinMode(GasPin, Arduino.SERVO); // GasPin=10
            } catch (Exception ex) {dunit = false;}
        if (LiveCam) if (dunit) while (true) { try {
            myVid = theVideo;
            dunit = false;
            if (myVid==null) break;
            dunit = myVid.Connect(CamFPS); // OK to fail true, cuz CamActive=false
            if (!dunit) break;
            CamTile = myVid.PixTile();
            nx = myVid.Dimz();
            CamTall = nx>>16;
            CamWide = nx&0xFFF;
            nx = CamTall*CamWide;
            CamPix = new byte[nx*4];
            if (CamTall==ScrHi) if (nx>1023) if (CamWide <= ScrWi)
                if ((CamTile==1) || (CamTile==3)) CamActive = (CamPix != null);
        } catch (Exception ex) {CamActive = false; dunit = false;}
            break;} //~while // (LiveCam)
        if (!dunit) {
            System.out.println("Connect failed"); // only early retn
            Stopit(-1);
            return;} //~if
        else if (titok != null) titok.start(); // too noisy in log
        if (theSim.GetFacing() == 0.0) if (theSim.GetPosn(false) == theSim.GetPosn(true))
        {}
        AxLR8(true,0); // initialize stopped
        SteerMe(true,0); // ..and straight ahead
        if (Calibrating !=0) theSim.SimStep(0);
        else if (StartLive) if (CamPix != null) if (theVideo != null)
            if (theVideo.Live()) {
                CameraView = true;
                theSim.SimStep(0);} //~if
        System.out.println("DrDemo " + sayso);
        setTitle("DriveDemo Example"); // was: this.setTitle etc..
        setSize(ScrWi+18,ScrHi+40); // make it larger for insets to come
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
        addMouseListener(this); // if 'implements MouseListen..'
        setVisible(true);}} //~DrDemo (drivedemo) (DM) (SD)