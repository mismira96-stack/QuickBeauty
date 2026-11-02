"""Reference-inspired story proposals, rendered as app-native photo composition."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import shutil, json

ROOT=Path(__file__).resolve().parent.parent
OUT=Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\story-template-v3-20261024')
OUT.mkdir(parents=True,exist_ok=True)
SOURCE=Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\imagegen\quickbeauty-park-portrait.png')
photo=ImageOps.exif_transpose(Image.open(SOURCE)).convert('RGB')
PEN=ROOT/'tools/design-assets/nanumpenscript/NanumPenScript-Regular.ttf'
SANS=Path('C:/Windows/Fonts/malgun.ttf')
W,H=1080,1920

def f(size,pen=False): return ImageFont.truetype(str(PEN if pen else SANS),size)
def fit(im,box):
    l,t,r,b=box;s=min((r-l)/im.width,(b-t)/im.height)
    p=im.resize((round(im.width*s),round(im.height*s)),Image.Resampling.LANCZOS)
    return p,(l+((r-l)-p.width)//2,t+((b-t)-p.height)//2)
def caption(im,value,xy,pen,color,center=False):
    font=f(66 if pen else 36,pen)
    d=ImageDraw.Draw(im)
    x,y=xy
    if center:x-=d.textlength(value,font=font)/2
    d.text((round(x),y),value,font=font,fill=color)
def scrim(im,top,bottom,start_alpha,end_alpha,color):
    overlay=Image.new('RGBA',(W,H),(0,0,0,0));d=ImageDraw.Draw(overlay)
    for y in range(top,bottom):
        t=(y-top)/max(1,bottom-top-1)
        d.line((0,y,W,y),fill=(*color,round(start_alpha*(1-t)+end_alpha*t)))
    return Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB')

def render(style,pen=True,with_caption=True):
    if style=='clean':
        im=Image.new('RGB',(W,H),'#FAFAF7')
        p,xy=fit(photo,(22,22,1058,1836));im.paste(p,xy)
        if with_caption:
            im=scrim(im,22,500,190,0,(250,250,247))
            caption(im,'오늘도, 좋은 날', (108,235),pen,'#323731')
    elif style=='soft':
        im=Image.new('RGB',(W,H),'#E4E6DF')
        p,xy=fit(photo,(74,184,1006,1510));im.paste(p,xy)
        if with_caption:caption(im,'잠깐의 산책', (540,1575),pen,'#353E36',True)
    elif style=='quiet':
        im=Image.new('RGB',(W,H),'#ECEEE8')
        p,xy=fit(photo,(0,0,W,H));im.paste(p,xy)
        if with_caption:
            im=scrim(im,1240,1920,0,165,(12,22,17))
            caption(im,'이 시간이 좋아', (116,1565),pen,'#FFFFFF')
    return im

styles=[('clean','Clean Margin','큰 사진 + 가벼운 여백'),('soft','Soft Tone','단색 + 정돈된 사진'),('quiet','Quiet Caption','사진 위에 작은 한 줄')]
for style,_,_ in styles:
    render(style).save(OUT/(style+'-handwriting.png'))
    render(style,False).save(OUT/(style+'-sans.png'))
    render(style,with_caption=False).save(OUT/(style+'-empty.png'))

def board(pen=True):
    im=Image.new('RGB',(1560,1210),'#111419');d=ImageDraw.Draw(im)
    d.text((48,30),'QuickBeauty / Story direction 03',font=f(32),fill='#F3F4F2')
    d.text((48,84),'같은 사진 · 크롭 없음 · '+('한글 손글씨' if pen else '기본 고딕')+' · 선택 문구',font=f(21),fill='#A6ABA6')
    for i,(style,name,sub) in enumerate(styles):
        x=48+i*504;y=144
        im.paste(render(style,pen).resize((456,811),Image.Resampling.LANCZOS),(x,y))
        d.text((x,y+838),name,font=f(27),fill='#F3F4F2')
        d.text((x,y+884),sub,font=f(20),fill='#A6ABA6')
    d.text((48,1148),'기존 AI 샘플 사진 / 디자인 제안 / 앱에는 아직 미반영 / 기본 문구는 빈 값',font=f(20),fill='#A6ABA6')
    return im
board().save(OUT/'comparison-handwriting.jpg',quality=95)
board(False).save(OUT/'comparison-sans.jpg',quality=95)

# Exact same layouts without captions, to assess the default empty-text result.
empty=Image.new('RGB',(1560,960),'#111419')
for i,(style,_,_) in enumerate(styles):empty.paste(render(style,with_caption=False).resize((456,811),Image.Resampling.LANCZOS),(48+i*504,48))
ImageDraw.Draw(empty).text((48,892),'문구 없는 기본값 / 글자용 그라데이션도 함께 사라집니다',font=f(24),fill='white')
empty.save(OUT/'comparison-empty.jpg',quality=95)
shutil.copy2(ROOT/'tools/design-assets/nanumpenscript/OFL.txt',OUT/'NanumPenScript-OFL.txt')
for file in OUT.glob('*.png'):
    with Image.open(file) as im:assert im.size==(1080,1920)
print(OUT)
