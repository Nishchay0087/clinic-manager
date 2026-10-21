#!/usr/bin/env python3
"""Destructive-to-demo-state HTTP checks. Use ONLY a development database with seed-demo.sql."""
import concurrent.futures, datetime, http.cookiejar, json, os, re, sys, urllib.error, urllib.parse, urllib.request, uuid
from zoneinfo import ZoneInfo
TODAY=datetime.datetime.now(ZoneInfo(os.environ.get("CLINIC_TIMEZONE","Asia/Kolkata"))).date()
BASE=os.environ.get('APP_BASE_URL','http://localhost:8080/clinic-manager')
PASS=os.environ.get('DEMO_PASSWORD','ClinicDemo!2026')
class Client:
    def __init__(self):
        self.cookies=http.cookiejar.CookieJar();self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies));self.csrf=''
    def request(self,path,data=None,form=False,headers=None,expect=200):
        h=headers or {}
        if data is not None:
            if isinstance(data,bytes):body=data
            elif form:body=urllib.parse.urlencode(data).encode();h['Content-Type']='application/x-www-form-urlencoded'
            else:body=json.dumps(data).encode();h['Content-Type']='application/json';h['X-CSRF-Token']=self.csrf
        else:body=None
        try:r=self.http.open(urllib.request.Request(BASE+path,body,h),timeout=20)
        except urllib.error.HTTPError as e:r=e
        payload=r.read();assert r.status==expect,(path,r.status,payload[:300])
        return json.loads(payload) if 'application/json' in r.headers.get('Content-Type','') else payload
    def token(self,page='/login'):
        html=self.request(page).decode();match=re.search(r'name="(?:csrf|csrf-token)" (?:value|content)="([^"]+)"',html);assert match,'CSRF missing';self.csrf=match.group(1)
    def login(self,email,password=PASS):
        self.token();self.request('/login',{'csrf':self.csrf,'email':email,'password':password},form=True);self.token('/app')
checks=[]
def ok(message):checks.append(message);print('PASS',message)
anon=Client();assert b'Welcome back' in anon.request('/login');assert b'--' in anon.request('/assets/css/app.css');assert len(anon.request('/api/doctors'))>=3;anon.request('/api/dashboard',expect=401);ok('Public JSP, assets, live directory and anonymous denial')
patient=Client();patient.login('patient@clinic.demo');doctor=Client();doctor.login('doctor@clinic.demo');admin=Client();admin.login('admin@clinic.demo')
for c in (patient,doctor,admin):assert c.request('/api/dashboard')['metrics']
patient.request('/api/users',expect=403);doctor.request('/api/settings',expect=403);
patient.request('/api/profile',b'{}',headers={'Content-Type':'application/json'},expect=403);ok('Three role logins, database dashboards, RBAC and CSRF')
new=Client();new.token('/register');email='smoke-'+uuid.uuid4().hex[:10]+'@example.test';new.request('/register',{'csrf':new.csrf,'name':'Fictional Smoke Patient','email':email,'password':PASS,'confirmPassword':PASS,'role':'ADMIN'},form=True);new.token('/app');assert new.request('/api/me')['role']=='PATIENT';ok('Registration cannot elevate role')
date=(TODAY+datetime.timedelta(days=2)).isoformat();slots=patient.request('/api/slots?doctorId=1&date='+date);assert len(slots)>2
body={'doctorId':1,'date':date,'startTime':slots[0],'reason':'Fictional smoke test'}
def race(c):
    try:return c.request('/api/appointments',body,expect=201)
    except AssertionError as e:
        assert '409' in str(e),e
        return None
with concurrent.futures.ThreadPoolExecutor(2) as pool:results=list(pool.map(race,[patient,new]))
assert sum(r is not None for r in results)==1
owner=patient if results[0] else new;other=new if results[0] else patient;a=next(r for r in results if r);aid=a['id'];other.request('/api/appointments/'+str(aid)+'/cancel',{},expect=403)
owner.request('/api/appointments/'+str(aid)+'/reschedule',{'date':date,'startTime':slots[1]});assert len(owner.request('/api/appointments/'+str(aid)+'/history'))>=2
owner.request('/api/appointments/'+str(aid)+'/cancel',{});assert slots[1] in patient.request('/api/slots?doctorId=1&date='+date);ok('Real MySQL concurrent booking, ownership, reschedule, history and cancellation')
# New patient establishes a care relationship before the PDF access check.
new.request('/api/appointments',{'doctorId':1,'date':date,'startTime':slots[2],'reason':'Fictional document care relationship'},expect=201)
pdfpath=sys.argv[1] if len(sys.argv)>1 else 'docs/fictional-report.pdf'
with open(pdfpath,'rb') as f:pdf=f.read()
boundary='clinic'+uuid.uuid4().hex
parts=[]
for k,v in {'category':'LAB_REPORT','title':'Fictional smoke PDF','description':'Automated development test only'}.items():parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+k+'"\r\n\r\n'+v+'\r\n').encode())
parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="fictional.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode()+pdf+b'\r\n');parts.append(('--'+boundary+'--\r\n').encode())
upload=new.request('/api/documents',b''.join(parts),headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-CSRF-Token':new.csrf},expect=201);did=upload['documentId']
assert new.request('/api/documents/'+str(did)).startswith(b'%PDF');assert doctor.request('/api/documents/'+str(did)).startswith(b'%PDF');patient.request('/api/documents/'+str(did),expect=403);admin.request('/api/documents/'+str(did),expect=403);assert new.request('/api/documents?q=FICTIONAL');ok('PDF upload, extraction/search, protected preview and cross-patient/admin denial')
new.request('/api/documents/'+str(did)+'/archive',{});new.request('/api/documents/'+str(did),expect=404);ok('Document retention archive hides content')
queue=doctor.request('/api/queue');assert queue
# Complete one currently checked-in fictional seeded visit.
visits=doctor.request('/api/appointments?date='+TODAY.isoformat());visit=next((v for v in visits if v['status']=='CHECKED_IN'),None)
assert visit, 'No checked-in visit today: prepare a fresh demo for the clinic date before this test'
if visit:
    doctor.request('/api/queue',{'appointmentId':visit['id'],'severity':'URGENT','reason':'Fictional smoke assessment'})
    doctor.request('/api/appointments/'+str(visit['id'])+'/status',{'status':'IN_PROGRESS'})
    doctor.request('/api/consultations',{'appointmentId':visit['id'],'clinicalNotes':'FICTIONAL smoke note','diagnosis':'Demonstration only','prescription':'No real prescription','complete':True})
    assert doctor.request('/api/records?patientId='+str(visit['patientId']))['consultations'];ok('Triage update, visit transitions and consultation persistence')
reports=admin.request('/api/reports');assert reports['daily'];assert b'Report,Label,Count' in admin.request('/api/reports?format=csv');assert admin.request('/api/audit');ok('Reports, CSV and audit')
new.request('/api/notifications/read',{});new.request('/api/profile',{'name':'Fictional Updated Patient'});assert new.request('/api/profile')['name']=='Fictional Updated Patient';ok('Notifications and profile persistence')
new.request('/api/password',{'currentPassword':PASS,'newPassword':PASS+'v2'});new.request('/api/me',expect=401);new.login(email,PASS+'v2');ok('Password change verifies current password and revokes the prior session')
docEmail='smokedoc-'+uuid.uuid4().hex[:8]+'@example.test';created=admin.request('/api/users',{'name':'Dr. Fictional Smoke','email':docEmail,'password':PASS,'role':'DOCTOR','specialization':'General Medicine','consultationFee':500})
admin.request('/api/users/'+str(created['id'])+'/doctor',{'specialization':'Dermatology','consultationFee':750,'profileDescription':'Fictional development profile'})
newDoctor=Client();newDoctor.login(docEmail);profile=newDoctor.request('/api/profile');assert profile['specialization']=='Dermatology'
newDoctor.request('/api/availability',{'dayOfWeek':datetime.date.fromisoformat(date).isoweekday(),'startTime':'09:00','endTime':'17:00','appointmentDuration':30})
assert patient.request('/api/slots?doctorId='+str(profile['doctorId'])+'&date='+date)
admin.request('/api/settings',{'reminder_hours':'24','triage_priority_order':'EMERGENCY,URGENT,ROUTINE'});admin.request('/api/settings',{'triage_priority_order':'EMERGENCY,URGENT,ROUTINE,EMERGENCY'},expect=400)
admin.request('/api/users/'+str(created['id'])+'/status',{'status':'INACTIVE'});newDoctor.request('/api/me',expect=401);ok('Admin doctor creation/edit, real availability, settings validation and deactivation')
uid=new.request('/api/me')['id'];reset=admin.request('/api/users/'+str(uid)+'/reset',{});token=urllib.parse.parse_qs(urllib.parse.urlsplit(reset['resetUrl']).query)['token'][0];resetter=Client();resetter.token('/reset?token='+token);resetter.request('/reset',{'csrf':resetter.csrf,'token':token,'password':PASS+'changed'},form=True);new.request('/api/me',expect=401);resetter.token('/reset?token='+token);resetter.request('/reset',{'csrf':resetter.csrf,'token':token,'password':PASS+'changed'},form=True,expect=400);ok('One-time admin reset revokes existing login')
print(f'{len(checks)} workflow groups passed against {BASE}')
