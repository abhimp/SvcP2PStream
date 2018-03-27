# make sure JSVM is in PATH
JSVMPATH=$(pwd)/../jsvm_9.19.5/bin
export PATH=$PATH:$JSVMPATH

# call svc_merge.py and create the yuv files for the segments
# svc_merge.py for base layer only:
python svc_merge.py bluesky-III-1080p.seg0-BL.264 bluesky-III-1080p.init.svc bluesky-III-1080p.seg0-L0.svc
H264AVCDecoderLibTestStatic bluesky-III-1080p.seg0-BL.264 bluesky-III-1080p.seg0-BL.yuv

# svc_merge.py for base layer + EL 1:
python svc_merge.py bluesky-III-1080p.seg0-EL1.264 bluesky-III-1080p.init.svc bluesky-III-1080p.seg0-L0.svc bluesky-III-1080p.seg0-L1.svc
H264AVCDecoderLibTestStatic bluesky-III-1080p.seg0-EL1.264 bluesky-III-1080p.seg0-EL1.yuv

# svc_merge.py for base layer + EL 1 + EL 2:
python svc_merge.py bluesky-III-1080p.seg0-EL2.264 bluesky-III-1080p.init.svc bluesky-III-1080p.seg0-L0.svc bluesky-III-1080p.seg0-L1.svc bluesky-III-1080p.seg0-L2.svc
H264AVCDecoderLibTestStatic bluesky-III-1080p.seg0-EL2.264 bluesky-III-1080p.seg0-EL2.yuv

# svc_merge.py for base layer + EL 1 + EL 2 + EL 3:
python svc_merge.py bluesky-III-1080p.seg0-EL3.264 bluesky-III-1080p.init.svc bluesky-III-1080p.seg0-L0.svc bluesky-III-1080p.seg0-L1.svc bluesky-III-1080p.seg0-L2.svc bluesky-III-1080p.seg0-L3.svc
H264AVCDecoderLibTestStatic bluesky-III-1080p.seg0-EL3.264 bluesky-III-1080p.seg0-EL3.yuv

# use mplayer to playback the three yuv files
mplayer -demuxer rawvideo -rawvideo w=640:h=360:format=i420:fps=24 bluesky-III-1080p.seg0-BL.yuv -loop 0 &
mplayer -demuxer rawvideo -rawvideo w=1280:h=720:format=i420:fps=24 bluesky-III-1080p.seg0-EL1.yuv -loop 0 &
mplayer -demuxer rawvideo -rawvideo w=1920:h=1080:format=i420:fps=24 bluesky-III-1080p.seg0-EL2.yuv -loop 0 &
mplayer -demuxer rawvideo -rawvideo w=1920:h=1080:format=i420:fps=24 bluesky-III-1080p.seg0-EL3.yuv -loop 0 &

# remove the files we created
# rm *.yuv
# rm *.svc
# rm *.264
