"""Code-native template explorations. Original photo pixels are only resized, never retouched."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageStat, ImageOps
import json

SOURCE = Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\imagegen\quickbeauty-park-portrait.png')
OUT = Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\story-template-v2-20261024')
OUT.mkdir(parents=True, exist_ok=True)
photo = ImageOps.exif_transpose(Image.open(SOURCE)).convert('RGB')
W,H = 1080,1920
CAPTION = '햇빛 좋은 날, 잠깐의 산책'

def font(size, weight='regular'):
    return ImageFont.truetype('C:/Windows/Fonts/'+('malgunbd.ttf' if weight=='bold' else 'malgun.ttf'),size)

def fit(im, box, anchor='center'):
    x,y,r,b=box
    s=min((r-x)/im.width,(b-y)/im.height)
    p=im.resize((round(im.width*s),round(im.height*s)),Image.Resampling.LANCZOS)
    px=x if anchor=='left' else r-p.width if anchor=='right' else x+((r-x)-p.width)//2
    py=y+((b-y)-p.height)//2
    return p,(px,py)

def text(im, value, xy, size=32, color='#303330',align='left'):
    d=ImageDraw.Draw(im)
    f=font(size)
    # Keep a quiet, single-line caption; shrink only when necessary.
    while d.textlength(value,font=f)>900 and size>26:
        size-=1; f=font(size)
    if align=='center': xy=(round(xy[0]-d.textlength(value,font=f)/2),xy[1])
    d.text(xy,value,font=f,fill=color)

# Sample foliage rather than skin: choose the strongest green-excess pixel bin,
# then heavily reduce saturation. No blur and no photo recoloring.
thumb=photo.resize((80,80))
greens=[p for p in thumb.getdata() if p[1]>p[0]*.96 and p[1]>p[2]*1.08]
mean=tuple(round(sum(p[c] for p in greens)/len(greens)) for c in range(3)) if greens else (130,145,135)
lum=sum(mean)/3
muted=tuple(round(lum*.68+v*.32) for v in mean)
soft=tuple(round(v*.20+247*.80) for v in muted)
deep=tuple(round(v*.65+196*.35) for v in muted)
light=tuple(round(v*.10+248*.90) for v in muted)

specs=[
 dict(id='01-clean-margin',name='Clean Margin',ko='정돈된 여백',bg='#FAFAF8',box=(54,170,1026,1510),caption=(540,1588),align='center',size=32,
      desc='큰 사진 · 얇은 여백 · 중앙 캡션',difficulty='낮음'),
 dict(id='02-soft-tone',name='Soft Tone',ko='사진에서 고른 한 가지 색',bg=soft,box=(96,250,984,1490),caption=(540,1570),align='center',size=34,
      desc='절제된 단색 · 중앙 사진 · 부드러운 대비',difficulty='낮음'),
 dict(id='03-split-story',name='Split Story',ko='사진과 문구의 분리',bg='#F7F8F5',box=(0,150,1080,1290),caption=(86,1510),align='left',size=40,
      desc='사진 7 : 여백 3 · 왼쪽 캡션',difficulty='낮음'),
 dict(id='04-edge-fit',name='Edge / Fit',ko='한쪽 가장자리로',bg='#F1F3F0',box=(0,96,1048,1610),caption=(48,1634),align='left',size=30,
      desc='한쪽 정렬 · 큰 사진 · 크롭 없이',difficulty='낮음'),
 dict(id='05-quiet-caption',name='Quiet Caption',ko='작은 문구, 넓은 사진',bg='#FCFCFC',box=(20,80,1060,1540),caption=(78,1625),align='left',size=28,
      desc='사진 우선 · 낮은 캡션 밴드 · 작은 글자',difficulty='낮음'),
 dict(id='06-duotone-frame',name='Duotone Frame',ko='두 가지 색의 온도',bg=soft,box=(108,260,972,1480),caption=(540,1570),align='center',size=34,
      desc='사진에서 추출한 2톤 · 블러 없는 그라데이션',difficulty='중간'),
]

def render(spec, with_text=True, source=photo):
    im=Image.new('RGB',(W,H),spec['bg'])
    if spec['id'].startswith('06'):
        # Broad diagonal two-tone field. No drop shadow or blurry copy of the photo.
        small=Image.new('RGB',(270,480))
        pix=small.load()
        for y in range(480):
            for x in range(270):
                t=(x/269*.35+y/479*.65)
                pix[x,y]=tuple(round(deep[c]*(1-t)+light[c]*t) for c in range(3))
        im=small.resize((W,H),Image.Resampling.BILINEAR)
    if spec['id'].startswith('03'):
        ImageDraw.Draw(im).rectangle((0,1344,1080,1920),fill='#E8ECE6')
    p,xy=fit(source,spec['box'],'left' if spec['id'].startswith('04') else 'center')
    im.paste(p,xy)
    if with_text: text(im,CAPTION,spec['caption'],spec['size'],align=spec['align'])
    return im

for spec in specs:
    render(spec).save(OUT/(spec['id']+'.png'))
    render(spec,False).save(OUT/(spec['id']+'-empty.png'))

def board(with_text=True):
    bw,bh=1680,2400
    im=Image.new('RGB',(bw,bh),'#F0F1F2')
    d=ImageDraw.Draw(im)
    d.text((66,40),'QuickBeauty / Story studies 02',font=font(36,'bold'),fill='#22272B')
    d.text((66,100),'같은 사진 · 크롭 없음 · 블러 / 그림자 / 워터마크 없음',font=font(22),fill='#656B72')
    for i,spec in enumerate(specs):
        x=66+(i%3)*526; y=174+(i//3)*1092
        tile=render(spec,with_text).resize((496,882),Image.Resampling.LANCZOS)
        im.paste(tile,(x,y))
        d.text((x,y+905),spec['name'],font=font(28,'bold'),fill='#262B2F')
        d.text((x,y+951),spec['desc'],font=font(19),fill='#697078')
    d.text((66,2350),'기존 프로젝트 AI 샘플 사진 / 디자인 제안 시안 / 기본 문구는 빈 값',font=font(20),fill='#697078')
    return im

board().save(OUT/'comparison.jpg',quality=95)
board(False).save(OUT/'comparison-empty.jpg',quality=95)

# Landscape geometry probe uses the same uncropped portrait photo letterboxed into
# a synthetic landscape source, so it is a layout test rather than a second photo.
landscape=Image.new('RGB',(1600,1000),'#DDE1D8')
p,xy=fit(photo,(0,0,1600,1000)); landscape.paste(p,xy)
for spec in specs[:2]: render(spec,True,landscape).save(OUT/(spec['id']+'-landscape-layout-check.png'))

(OUT/'specs.json').write_text(json.dumps(specs,ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'README.md').write_text('''# QuickBeauty ス토리 템플릿 v2 시안

- 앱 코드는 변경하지 않은 디자인 제안입니다.
- 모든 개별 PNG는 1080×1920입니다. comparison.jpg는 6종 비교, comparison-empty.jpg는 문구 없는 비교입니다.
- 원본은 기존 프로젝트 AI 생성 샘플이며 사진 보정·잘라내기는 하지 않았습니다.
- 문구는 비교를 위한 예시입니다. 실제 기본값은 빈 문자열입니다.
- Edge Crop 제안은 구도 보존을 위해 Edge / Fit으로 조정했습니다. 자동 크롭은 이번 시안에 없습니다.
- 대표색은 피부색을 피하도록 녹색 후보군에서 추출해 채도를 낮췄습니다. 앱에서는 후보가 없을 때의 중성색 fallback과 글자 대비 검사도 필요합니다.
- landscape-layout-check는 동일 사진을 합성 가로 캔버스에 담은 레이아웃 확인용이며 실제 가로 사진 테스트를 대신하지 않습니다.
- 텍스트는 모두 하단 인스타 UI에 가리지 않도록 대체로 y=1680 안쪽에 배치했으나, 실제 인스타 UI에서의 확인은 남아 있습니다.
- 시안 폰트는 Windows 맑은 고딕을 사용했습니다. Android에서는 기본 sans-serif로 대체한 뒤 글자 폭과 느낌을 검증해야 합니다.
''',encoding='utf-8')
print(str(OUT/'comparison.jpg'))
