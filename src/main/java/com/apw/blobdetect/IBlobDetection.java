package com.apw.blobdetect;

import com.apw.oldimage.IImage;

import java.util.List;

//Intereface Blob Detection
public interface IBlobDetection {
    List<Blob> getBlobs(IImage image);
}
