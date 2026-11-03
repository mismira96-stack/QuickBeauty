import os, json, hashlib
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
root=Path(__file__).resolve().parents[1]
out=root/'release/1.1.8'
meta=json.loads((out/'play-metadata.json').read_text(encoding='utf-8'))
p=meta['package']
s=build('androidpublisher','v3',credentials=service_account.Credentials.from_service_account_file(os.environ['GOOGLE_APPLICATION_CREDENTIALS'],scopes=['https://www.googleapis.com/auth/androidpublisher']),cache_discovery=False)
def record(name,data):
 (out/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def state(e):
 tracks=s.edits().tracks().list(packageName=p,editId=e).execute()
 listings=s.edits().listings().list(packageName=p,editId=e).execute()
 assets={}
 for l in listings.get('listings',[]):
  lang=l['language'];assets[lang]={}
  for kind in ['icon','phoneScreenshots','featureGraphic']:
   assets[lang][kind]=s.edits().images().list(packageName=p,editId=e,language=lang,imageType=kind).execute().get('images',[])
 return {'tracks':tracks,'listings':listings,'assets':assets}
e=s.edits().insert(packageName=p,body={}).execute()['id'];committed=False
try:
 before=state(e);record('state-before.json',before)
 production=next(t for t in before['tracks']['tracks'] if t['track']=='production')
 assert production.get('releases') and all(r.get('status')=='completed' and r.get('versionCodes')==['20'] for r in production['releases']), 'Production differs from approved baseline; inspect before proceeding'
 aab=out/'QuickBeauty-1.1.8-21.aab';digest=hashlib.sha256(aab.read_bytes()).hexdigest()
 bundle=s.edits().bundles().upload(packageName=p,editId=e,media_body=MediaFileUpload(str(aab),mimetype='application/octet-stream',resumable=True,chunksize=8*1024*1024)).execute();record('bundle-upload.json',bundle)
 assert str(bundle['versionCode'])=='21' and bundle['sha256'].lower()==digest,'Bundle version/hash mismatch'
 release={'name':meta['releaseName'],'versionCodes':['21'],'status':'completed','releaseNotes':meta['releaseNotes']}
 track=s.edits().tracks().update(packageName=p,editId=e,track='production',body={'track':'production','releases':[release]}).execute();record('track-update.json',track)
 record('validation.json',s.edits().validate(packageName=p,editId=e).execute())
 # Explicit user authorization to release AND send for review. Never cancel an existing review.
 response=s.edits().commit(packageName=p,editId=e,changesInReviewBehavior='ERROR_IF_IN_REVIEW').execute();committed=True;record('commit.json',response)
 print('Commit succeeded: production update submitted for Google processing/review.',flush=True)
 check=s.edits().insert(packageName=p,body={}).execute()['id']
 try:
  after=state(check);record('state-after.json',after)
  prod=next(t for t in after['tracks']['tracks'] if t['track']=='production')
  assert len(prod['releases'])==1 and prod['releases'][0]['versionCodes']==['21'] and prod['releases'][0]['status']=='completed'
  assert after['listings']==before['listings'],'Listing text/video changed unexpectedly'
  for lang,kinds in before['assets'].items():
   for kind,images in kinds.items():
    assert [x['sha256'] for x in images]==[x['sha256'] for x in after['assets'][lang][kind]],'Unrelated image change'
  other=lambda st:[t for t in st['tracks']['tracks'] if t['track']!='production']
  assert other(after)==other(before),'Other tracks changed'
  result={'versionName':'1.1.8','versionCode':21,'track':'production','trackStatus':'completed','bundleSha256':digest,'committed':True,'reviewSubmissionRequested':True,'reviewBehavior':'ERROR_IF_IN_REVIEW','publicationApprovalVerified':False,'otherListingTextVideoImagesAndTracksUnchanged':True}
  record('release-result.json',result);print(json.dumps(result),flush=True)
 finally:s.edits().delete(packageName=p,editId=check).execute()
finally:
 if not committed:
  try:s.edits().delete(packageName=p,editId=e).execute()
  except Exception:pass
