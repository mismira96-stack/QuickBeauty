"""Typography-first, app-native story layout proposals; no photo retouching."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
import shutil

ROOT=Path(__file__).resolve().parent.parent
OUT=Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\story-template-v4-20261024')
OUT.mkdir(parents=True,exist_ok=True)
photo=ImageOps.exif_transpose(Image.open(Path(r'C:\Users\mismi\OneDrive\문서\ChatGPT\QuickBeauty\output\imagegen\quickbeauty-park-portrait.png'))).convert('RGB')
ASSETS=ROOT/'tools/design-assets'
FONTS=[('고운바탕','gowunbatang/GowunBatang-Bold.ttf'),('고운돋움','gowundodum/GowunDodum-Regular.ttf'),('나눔손글씨 펜','nanumpenscript/NanumPenScript-Regular.ttf')]
def font(i,size):return ImageFont.truetype(str(ASSETS/FONTS[i][1]),size)
def ui(size):return ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',size)
def fit(im,box):
    x,y,r,b=box;s=min((r-x)/photo.width,(b-y)/photo.height)
    p=photo.resize((round(photo.width*s),round(photo.height*s)),Image.Resampling.LANCZOS)
    im.paste(p,(x+((r-x)-p.width)//2,y+((b-y)-p.height)//2))
def center(d,value,y,f,color='#34382F'):
    d.text((540-d.textlength(value,font=f)/2,y),value,font=f,fill=color)
def fade(im):
    overlay=Image.new('RGBA',im.size)
    d=ImageDraw.Draw(overlay)
    for y in range(1160,1920):d.line((0,y,1080,y),fill=(9,22,16,round(205*(y-1160)/760)))
    return Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB')

# Controlled font comparison: same photo, composition, text and output dimensions.
font_board=Image.new('RGB',(1560,1240),'#161A1D');d=ImageDraw.Draw(font_board)
d.text((48,34),'QuickBeauty / 한글 폰트부터 다시',font=ui(34),fill='white')
d.text((48,90),'동일 사진 · 동일 문구 · 동일 배치 / 단어가 아니라 글자 자체의 분위기를 비교',font=ui(20),fill='#A9AFAA')
for i,(name,_) in enumerate(FONTS):
    im=Image.new('RGB',(1080,1920),'#F8F7F3');fit(im,(70,120,1010,1430))
    draw=ImageDraw.Draw(im)
    center(draw,'오늘도 좋은 날',1502,font(i,74 if i!=2 else 96))
    center(draw,'2026.10.24',1640,ui(29),'#7F837B')
    im.save(OUT/f'font-{i+1}.png')
    x=48+i*504;font_board.paste(im.resize((456,811),Image.Resampling.LANCZOS),(x,155))
    d.text((x,1000),name,font=ui(28),fill='white')
    # Closeup stays vector-sharp in the board instead of only showing a tiny caption.
    d.text((x,1060),'오늘도 좋은 날',font=font(i,43 if i!=2 else 56),fill='#F5F4EE')
d.text((48,1186),'비교를 위한 예시 문구와 날짜 / 실제 기본값은 빈 값',font=ui(20),fill='#A9AFAA')
font_board.save(OUT/'font-comparison.jpg',quality=95)

names=['기록 카드','사이드 노트','타이틀 스토리']
subs=['명조 제목 + 정돈된 여백','좁은 메모 영역 + 손글씨','큰 글자 + 사진 위의 대비']
layout_board=Image.new('RGB',(1560,1250),'#161A1D');d=ImageDraw.Draw(layout_board)
d.text((48,34),'QuickBeauty / 결과물에 표정 만들기',font=ui(34),fill='white')
d.text((48,90),'폰트 · 크기 · 정렬 · 색면을 함께 설계 / 같은 사진, 크롭 없음',font=ui(20),fill='#A9AFAA')
for i in range(3):
    im=Image.new('RGB',(1080,1920),'#F8F7F3')
    if i==0:
        fit(im,(50,86,1030,1380));draw=ImageDraw.Draw(im)
        draw.line((104,1465,220,1465),fill='#9AA18E',width=2)
        draw.text((104,1500),'오늘도 좋은 날',font=font(0,72),fill='#32382E')
        draw.text((108,1630),'2026.10.24',font=ui(28),fill='#7D8379')
    elif i==1:
        im=Image.new('RGB',(1080,1920),'#DDE1D5')
        fit(im,(0,65,825,1855));draw=ImageDraw.Draw(im)
        draw.rectangle((838,0,1080,1920),fill='#F2EFE8')
        draw.text((876,252),'2026',font=ui(29),fill='#777C72')
        draw.text((876,302),'10.24',font=ui(29),fill='#777C72')
        draw.line((881,418,881,650),fill='#8D9389',width=2)
        for n,line in enumerate(['오늘도','좋은 날']):draw.text((867,786+n*104),line,font=font(2,75),fill='#424A3D')
    else:
        fit(im,(0,0,1080,1920));im=fade(im);draw=ImageDraw.Draw(im)
        draw.text((88,1380),'오늘도',font=font(1,94),fill='white')
        draw.text((88,1500),'좋은 날',font=font(1,94),fill='white')
        draw.text((93,1670),'2026.10.24',font=ui(29),fill='#E0E7DA')
    im.save(OUT/f'layout-{i+1}.png')
    x=48+i*504;layout_board.paste(im.resize((456,811),Image.Resampling.LANCZOS),(x,155))
    d.text((x,1000),names[i],font=ui(28),fill='white')
    d.text((x,1050),subs[i],font=ui(21),fill='#A9AFAA')
d.text((48,1190),'기존 AI 샘플 사진 / 디자인 제안 / 자동 줄바꿈·날짜 분리는 아직 앱에 미반영',font=ui(20),fill='#A9AFAA')
layout_board.save(OUT/'layout-comparison.jpg',quality=95)
for family in ['gowunbatang','gowundodum','nanumpenscript']:
    shutil.copy2(ASSETS/family/'OFL.txt',OUT/(family+'-OFL.txt'))
(OUT/'README.md').write_text('''# 스토리 템플릿 v4 — 타이포그래피 검토

사용자 피드백: 기존 3종이 너무 단순하며 작은 손글씨 한 줄만으로는 쓰고 싶은 결과가 나오지 않는다.

- font-comparison.jpg: 사진·배치·문구를 고정하고 고운바탕, 고운돋움, 나눔손글씨 펜을 비교한다. 광학적 크기 보정을 위해 손글씨는 더 크게 표시한다.
- layout-comparison.jpg: 기록 카드 / 사이드 노트 / 타이틀 스토리 3종 제안. 개별 PNG는 1080×1920이다.
- 사용자 조작의 단순함과 결과물의 단순함을 구분한다. 하나의 입력값을 템플릿의 제목/메모 영역에서 자동 줄바꿈하는 방향을 제안한다.
- 예시 날짜는 선택 기능 제안이며 기본으로 강제 삽입하자는 뜻이 아니다. 날짜 표시 여부를 문구와 분리하는 작업은 아직 앱에 적용하지 않았다.
- 사진은 기존 프로젝트 AI 생성 샘플이다. 잘라내거나 보정하지 않았다. 사용자의 실제 사진으로 착각하지 않도록 한다.
- 사진이 4:3, 1:1일 때는 각 레이아웃의 적합성을 별도로 확인해야 한다. 극단 비율에서 사진이 작아지면 템플릿 추천 또는 사용자 크롭 선택을 검토해야 한다.
- 폰트는 Google Fonts 공식 저장소에서 받았으며 OFL 원문을 포함한다. 새 폰트 2종은 아직 APK에 번들하지 않았다.
- 출처: https://github.com/google/fonts/tree/main/ofl/gowunbatang 및 https://github.com/google/fonts/tree/main/ofl/gowundodum
- 이 시안에서는 제목 2줄 및 날짜 위치를 검토한다. 최종 인스타그램 UI 안전 영역과 한글 30자 줄바꿈은 Android 구현 전에 정해야 한다.
- 앱 코드·APK·구글 드라이브 업로드본은 변경하지 않았다. 디자인 검토용 산출물이다.
''',encoding='utf-8')
for p in OUT.glob('*.png'):
    with Image.open(p) as im:assert im.size==(1080,1920)
print(OUT)
