# Routes and API

All routes below are relative to `/clinic-manager`. Responses are JSON unless noted. API writes require `X-CSRF-Token` from the application page's `csrf-token` meta element and the session cookie. Auth forms use a hidden `csrf` field. No client may choose its effective role.

| Route | Method | Access / behavior |
|---|---|---|
| `/`, `/directory`, `/doctors` | GET | Public landing and live doctor directory |
| `/login`, `/register` | GET, POST | Auth forms; registration always PATIENT |
| `/logout` | POST | Invalidate session |
| `/reset` | GET, POST | Expiring single-use administrator-issued token, CSRF protected |
| `/app` | GET | Role-specific shell; sidebar routes use URL hashes |
| `/api/me`, `/api/dashboard` | GET | Current user / scoped database metrics |
| `/api/doctors` | GET | Public doctor search: q, specialty, page, pageSize |
| `/api/slots` | GET | Authenticated; doctorId and date; returns valid available times |
| `/api/appointments` | GET | Role-scoped list; q, date, from, to, status, doctorId, patientId, specialization, page, pageSize |
| `/api/appointments` | POST | Patient's own booking; admin must include patientId; doctorId, date, startTime, reason, notes |
| `/api/appointments/{id}/history` | GET | Authorized appointment change history |
| `/api/appointments/{id}/cancel` | POST | Authorized cancellation within configured cutoff |
| `/api/appointments/{id}/reschedule` | POST | date, startTime; same doctor, revalidated transactionally |
| `/api/appointments/{id}/status` | POST | Staff only; status, validated transition |
| `/api/availability` | GET, POST | Doctor's own hours; save single day or weekly array with appointmentDuration |
| `/api/unavailability` | POST | Doctor; start/end ISO datetimes and reason, rejects booking conflicts |
| `/api/records` | GET | Own patient or treating doctor; patientId required for doctor |
| `/api/consultations` | POST | Treating doctor; appointmentId, clinicalNotes, diagnosis, prescription, followUpDate, complete |
| `/api/documents` | GET | Scoped metadata and extracted text; q, patientId, category, from, to, page, pageSize |
| `/api/documents` | POST multipart | file, patientId (doctor), category, title, description; PDF ≤10 MB |
| `/api/documents/{id}` | GET | Authorized PDF stream; download=true for attachment |
| `/api/documents/{id}/archive` | POST | Authorized patient/clinician; retains physical file |
| `/api/queue` | GET, POST | Staff only; reads prioritized queue; writes appointmentId, severity, reason |
| `/api/users` | GET | Admin search q/role/page/pageSize |
| `/api/users` | POST | Admin creates doctor/patient; name,email,password,role,specialization,consultationFee |
| `/api/users/{id}/doctor` | POST | Admin changes specialization,consultationFee,profileDescription |
| `/api/users/{id}/status` | POST | Admin activates/deactivates non-admin accounts |
| `/api/users/{id}/reset` | POST | Admin creates one-time reset URL, returned only to requester |
| `/api/settings` | GET, POST | Admin clinic settings; newSpecialty optionally adds specialty |
| `/api/reports` | GET | Admin; from/to; format=csv exports matching groups |
| `/api/audit` | GET | Admin paginated activity |
| `/api/notifications` | GET | Current user notifications |
| `/api/notifications/read` | POST | Mark current user's notifications read |
| `/api/profile` | GET, POST | Current user permitted profile fields |
| `/api/password` | POST | currentPassword,newPassword; current-password verification and session revocation |

Appointment statuses: BOOKED → CONFIRMED or CHECKED_IN; CONFIRMED → CHECKED_IN; CHECKED_IN → IN_PROGRESS; IN_PROGRESS → COMPLETED with clinical record. BOOKED/CONFIRMED may be CANCELLED within rules or marked NO_SHOW by staff after visit time. Completion/clinical changes are clinician-controlled. Dates cannot be backdated for booking. Emergencies require real immediate clinical handling; queue priority alone is not emergency care.

Errors use `{ "error": "Safe explanation" }` with 400 validation, 401 login, 403 forbidden/CSRF, 404 unavailable, 409 conflict, 413 too large, 429 throttling, or 500 unexpected failure. Browser fetch handles these with visible feedback. List APIs return arrays and cap sizes at 100. There is no unrestricted all-patients clinical endpoint or public document sharing route.
