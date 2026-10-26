package com.clinic.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import javafx.application.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.prefs.Preferences;

/** Native JavaFX scene graph. All server work runs off the JavaFX application thread. */
public final class ClinicApp extends Application {
    private Stage stage;
    private final BorderPane shell=new BorderPane();
    private final Label status=new Label("Connect to your clinic server to begin.");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"clinic-http");t.setDaemon(true);return t;});
    private final Preferences preferences=Preferences.userRoot().node(System.getProperty("clinic.preferences.node","com/clinic/desktop"));
    private ClinicClient client;
    private JsonNode user;
    private VBox content;
    private Runnable refresh;
    private String role;
    private final String css=Objects.requireNonNull(getClass().getResource("clinic.css")).toExternalForm();
    @Override public void start(Stage stage){this.stage=stage;stage.setTitle("E-Healthcare • Clinic Manager");var scene=new Scene(shell,1280,820);scene.getStylesheets().add(css);stage.setScene(scene);stage.setMinWidth(960);stage.setMinHeight(680);status.getStyleClass().add("statusbar");status.setMaxWidth(Double.MAX_VALUE);shell.setBottom(status);login(false);stage.show();}
    @Override public void stop(){worker.shutdownNow();if(client!=null)client.close();}
    public static void main(String[] args){launch(args);}
    private Label label(String value,String style){var l=new Label(value);l.getStyleClass().add(style);l.setWrapText(true);return l;}
    private Button button(String title,Runnable action){var b=new Button(title);b.setOnAction(e->action.run());return b;}
    private Button primary(String title,Runnable action){var b=button(title,action);b.getStyleClass().add("primary");return b;}
    private HBox row(Node... nodes){var h=new HBox(10,nodes);h.setAlignment(Pos.CENTER_LEFT);return h;}
    private ScrollPane scroll(Node node){var s=new ScrollPane(node);s.setFitToWidth(true);return s;}
    private static String text(JsonNode node,String key){return node.path(key).asText("");}
    private static String id(JsonNode node){return text(node,"id");}
    private static List<JsonNode> rows(JsonNode node){var out=new ArrayList<JsonNode>();if(node.isArray())node.forEach(out::add);return out;}
    private String query(String value){return ClinicClient.encode(value);}
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
    private <T>void load(String message,Work<T> work,Consumer<T> done){
        shell.setDisable(true);status.setText(message);
        worker.submit(()->{try{T result=work.run();Platform.runLater(()->{shell.setDisable(false);status.setText("Connected • "+(client==null?"":client.server()));try{done.accept(result);}catch(Exception e){error(e);}});}catch(Exception e){Platform.runLater(()->{shell.setDisable(false);error(e);});}});
    }
    private void error(Exception e){status.setText("Action could not be completed.");if(e instanceof java.net.ConnectException){String server=client==null?"the configured server":client.server();var warning=new Alert(Alert.AlertType.ERROR,"Cannot reach "+server+"\n\nFor this Mac, open another terminal in the project folder and run:\n./scripts/start-clinic.sh\n\nLeave that terminal open, then use this Server URL:\nhttp://localhost:8083/clinic-manager\n\nFor a remote clinic, check its server URL and network connection.",ButtonType.OK);warning.initOwner(stage);warning.setHeaderText("The clinic backend is not running or the address is incorrect");warning.showAndWait();return;}var a=new Alert(Alert.AlertType.ERROR,e.getMessage()==null?"Check the server connection and try again.":e.getMessage(),ButtonType.OK);a.initOwner(stage);a.setHeaderText("Please check and try again");a.showAndWait();}
    private void message(String title,String body){var a=new Alert(Alert.AlertType.INFORMATION,body,ButtonType.OK);a.initOwner(stage);a.setHeaderText(title);a.showAndWait();}
    private void confirm(String title,Runnable accepted){var a=new Alert(Alert.AlertType.CONFIRMATION,title,ButtonType.CANCEL,ButtonType.OK);a.initOwner(stage);a.setHeaderText("Confirm action");if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)accepted.run();}
    private void mutate(String route,Object values){load("Saving changes…",()->client.post(route,values),r->refresh.run());}
    private void login(boolean registration){
        shell.setLeft(null);user=null;role=null;
        var f=new Form();
        // Remove the address accidentally persisted by the original UI smoke test.
        if(preferences.get("server","").matches("http://localhost:8082/clinic-manager/?"))preferences.remove("server");
        String url=System.getenv().getOrDefault("CLINIC_SERVER_URL",preferences.get("server","http://localhost:8083/clinic-manager"));
        f.input("Server URL","server",url);if(registration)f.input("Full name","name","");f.input("Email address","email","");f.password("Password","password");if(registration)f.password("Confirm password","confirmPassword");
        var box=new VBox(18,label("+  e-healthcare","brand"),label(registration?"Create a patient account":"Welcome to your clinic","title"),label("One clinic. Shared records. Patient, doctor and administrator workspaces.","subtitle"),f.box);
        box.getStyleClass().add("card");box.setMaxWidth(500);
        var sign=primary(registration?"Create account":"Sign in",()->{
            var values=f.values();String server=values.get("server").toString();
            try{ClinicClient.normalize(server);}catch(Exception e){error(e);return;}
            if(!server.startsWith("https://")&&!server.contains("localhost")&&!server.contains("127.0.0.1")){
                var a=new Alert(Alert.AlertType.CONFIRMATION,"This connection uses HTTP. Use HTTPS for a hosted clinic; HTTP is suitable only for a trusted local demonstration.",ButtonType.CANCEL,ButtonType.OK);a.initOwner(stage);if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            }
            load("Signing in…",()->{if(client!=null)client.close();client=new ClinicClient(server);return registration?client.register(values.get("name").toString(),values.get("email").toString(),values.get("password").toString(),values.get("confirmPassword").toString()):client.login(values.get("email").toString(),values.get("password").toString());},u->{preferences.put("server",server);user=u;role=text(u,"role");workspace();});
        });sign.setMaxWidth(Double.MAX_VALUE);box.getChildren().addAll(sign,button(registration?"Back to sign in":"New patient? Create an account",()->login(!registration)),button("Use an administrator reset link",()->{var reset=new Form();reset.input("Reset link or token","token","");reset.password("New password","password");reset.password("Confirm password","confirmPassword");String server=f.values().get("server").toString();form("Reset account password",reset,"Reset password",b->{try(var connection=new ClinicClient(server)){connection.resetPassword(b.get("token").toString(),b.get("password").toString(),b.get("confirmPassword").toString());}return JsonNodeFactory.instance.objectNode();},r->{message("Password updated","Sign in with your new password.");login(false);},false);}),label("Doctor accounts are created by the clinic administrator.","subtitle"));
        var loginScroll=scroll(box);loginScroll.setMaxWidth(520);var center=new StackPane(loginScroll);center.setPadding(new Insets(40));shell.setCenter(center);
    }
    private void workspace(){
        var nav=new VBox(8,label("+  e-healthcare","brand"),label("CLINIC MANAGER","subtitle"),new Separator(),label(text(user,"name"),"title"),label(role,"subtitle"));nav.setPrefWidth(245);nav.getStyleClass().add("sidebar");
        nav.getChildren().add(button("Overview",this::dashboard));
        if(role.equals("PATIENT"))nav.getChildren().add(button("Find a doctor",()->doctors(1,"")));
        nav.getChildren().add(button("Appointments",()->appointments(1,"")));
        if(!role.equals("PATIENT"))nav.getChildren().add(button("Severity & patient queue",this::queue));
        if(!role.equals("ADMIN"))nav.getChildren().add(button("Medical records",()->{if(role.equals("PATIENT"))records(null);else choosePatient(this::records);}));
        nav.getChildren().add(button("Documents",()->documents(1,"","")));
        if(role.equals("DOCTOR"))nav.getChildren().add(button("Availability",this::availability));
        if(role.equals("ADMIN"))nav.getChildren().addAll(button("User management",()->users(1,"")),button("Reports & analytics",()->reports(LocalDate.now().minusDays(29),LocalDate.now())),button("Clinic settings",this::settings),button("Audit log",()->audit(1)));
        nav.getChildren().addAll(button("Notifications",this::notifications),button("Profile & password",this::profile));var spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);nav.getChildren().addAll(spacer,button("Sign out",()->load("Signing out…",()->{try{client.logout();return "";}catch(Exception e){return "Signed out locally. The server was unavailable; the remote token expires automatically.";}},result->{login(false);if(!result.isBlank())status.setText(result);})));shell.setLeft(nav);dashboard();
    }
    private void page(String title,String subtitle,Runnable refresh){this.refresh=refresh;content=new VBox(18,label(title,"title"),label(subtitle,"subtitle"));content.setPadding(new Insets(28));shell.setCenter(content);}
    private TableView<JsonNode> table(JsonNode data,String... columns){
        var t=new TableView<JsonNode>(FXCollections.observableArrayList(rows(data)));t.setPlaceholder(new Label("No records yet. New activity will appear here."));
        for(String column:columns){String[] pair=column.split(":",2);var c=new TableColumn<JsonNode,String>(pair[0]);c.setCellValueFactory(v->new ReadOnlyStringWrapper(text(v.getValue(),pair[1])));c.setPrefWidth(pair[1].toLowerCase().contains("name")?170:135);t.getColumns().add(c);}
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);t.setMinHeight(200);VBox.setVgrow(t,Priority.ALWAYS);return t;
    }
    private JsonNode selected(TableView<JsonNode> table){var item=table.getSelectionModel().getSelectedItem();if(item==null)message("Select a record","Choose a row in the table first.");return item;}
    private void selection(TableView<JsonNode> table,Consumer<JsonNode> action){var item=selected(table);if(item!=null)action.accept(item);}
    private HBox paging(int page,int count,IntConsumer go){var previous=button("← Previous",()->go.accept(page-1));previous.setDisable(page==1);var next=button("Next →",()->go.accept(page+1));next.setDisable(count<25);return row(previous,new Label("Page "+page),next);}
    private void dashboard(){load("Loading overview…",()->client.get("dashboard"),d->{
        page("Hello, "+text(user,"name"),"Your clinic activity, stored in the shared MySQL database.",this::dashboard);var metrics=new FlowPane(12,12);
        for(var m:d.path("metrics")){var card=new VBox(6,label(text(m,"label"),"subtitle"),label(text(m,"value"),"metric"));card.setPrefWidth(180);card.getStyleClass().add("card");metrics.getChildren().add(card);}content.getChildren().add(metrics);
        content.getChildren().add(row(primary(role.equals("PATIENT")?"Book an appointment":"View appointments",()->{if(role.equals("PATIENT"))doctors(1,"");else appointments(1,"");}),button("Refresh",this::dashboard)));
        content.getChildren().add(table(d.path("appointments"),"Patient:patientName","Doctor:doctorName","Date:date","Time:startTime","Status:status"));
    });}
    private void doctors(int page,String search){load("Loading doctors…",()->client.get("doctors?pageSize=25&page="+page+"&q="+query(search)),d->{page("Find a doctor","Choose a doctor, then an available appointment time.",()->doctors(page,search));var q=new TextField(search);q.setPromptText("Search doctors");var t=table(d,"Doctor:name","Specialty:specialization","Fee:consultationFee","Minutes:appointmentDuration");content.getChildren().addAll(row(q,button("Search",()->doctors(1,q.getText())),primary("Book with selected doctor",()->selection(t,r->booking(r,null)))),t,paging(page,d.size(),p->doctors(p,search)));});}
    private void booking(JsonNode doctor,JsonNode existing){
        String doctorId=existing==null?id(doctor):text(existing,"doctorId"),doctorName=existing==null?text(doctor,"name"):text(existing,"doctorName");
        var f=new Form();f.box.getChildren().add(label(doctorName,"title"));var date=f.date("Appointment date","date",existing==null?LocalDate.now().plusDays(1):LocalDate.parse(text(existing,"date")));var slots=f.choice("Available time","startTime",List.of(),"");
        if(existing==null){f.area("Reason for visit","reason","");if(role.equals("ADMIN"))f.input("Patient ID","patientId","");}
        var loadSlots=button("Load available times",()->{if(date.getValue()==null){message("Choose a date","Select an appointment date first.");return;}String day=date.getValue().toString();load("Checking availability…",()->client.get("slots?doctorId="+doctorId+"&date="+day),r->{if(day.equals(Objects.toString(date.getValue(),""))){slots.getItems().setAll(rows(r).stream().map(JsonNode::asText).toList());slots.setValue(null);if(slots.getItems().isEmpty())message("No available slots","Choose another date. The doctor must set working hours before bookings are available.");}});});
        date.valueProperty().addListener((o,a,b)->{slots.getItems().clear();slots.setValue(null);});f.box.getChildren().add(loadSlots);
        form(existing==null?"Book appointment":"Reschedule appointment",f,"Review and confirm",values->{if(values.get("startTime").toString().isBlank())throw new IllegalArgumentException("Load and select an available time.");values.put("doctorId",doctorId);return client.post(existing==null?"appointments":"appointments/"+id(existing)+"/reschedule",values);},r->appointments(1,""),true);
    }
    private void appointments(int page,String day){load("Loading appointments…",()->client.get("appointments?pageSize=25&page="+page+(day.isBlank()?"":"&date="+day)),d->{
        page("Appointments","Select a visit to manage it. Clinical actions are available on the appointment date.",()->appointments(page,day));var date=new DatePicker(day.isBlank()?null:LocalDate.parse(day));var t=table(d,"ID:id","Patient:patientName","Patient ID:patientId","Doctor:doctorName","Date:date","Time:startTime","Status:status");
        content.getChildren().add(row(date,button("Filter date",()->appointments(1,Objects.toString(date.getValue(),""))),button("All dates",()->appointments(1,"")),button("Refresh",refresh)));
        var actions=new FlowPane(8,8);actions.getChildren().addAll(button("History",()->selection(t,r->load("Loading history…",()->client.get("appointments/"+id(r)+"/history"),h->showTable("Appointment history",h,"From:oldStatus","To:newStatus","Reason:reason","When:createdAt")))),button("Reschedule",()->selection(t,r->booking(null,r))),button("Cancel visit",()->selection(t,r->confirm("Cancel this appointment?",()->mutate("appointments/"+id(r)+"/cancel",Map.of())))));
        if(!role.equals("PATIENT")){actions.getChildren().addAll(button("Check in",()->selection(t,r->mutate("appointments/"+id(r)+"/status",Map.of("status","CHECKED_IN")))),button("Mark no-show",()->selection(t,r->confirm("Mark this visit as a no-show?",()->mutate("appointments/"+id(r)+"/status",Map.of("status","NO_SHOW"))))));}
        if(role.equals("DOCTOR"))actions.getChildren().addAll(primary("Start / continue consultation",()->selection(t,this::beginConsultation)),button("Patient history",()->selection(t,r->records(text(r,"patientId")))));
        if(role.equals("ADMIN"))actions.getChildren().add(button("Book appointment",()->load("Loading doctors…",()->client.get("doctors?pageSize=100"),r->{var doctors=new LinkedHashMap<String,JsonNode>();for(var doctor:r)doctors.put(text(doctor,"name")+" • "+text(doctor,"specialization")+" (#"+id(doctor)+")",doctor);var choices=new ChoiceDialog<String>(null,doctors.keySet());choices.initOwner(stage);choices.setTitle("Choose doctor");choices.setHeaderText("Select a doctor");choices.showAndWait().ifPresent(x->booking(doctors.get(x),null));})));
        content.getChildren().addAll(actions,t,paging(page,d.size(),p->appointments(p,day)));
    });}
    private void queue(){load("Loading priority queue…",()->client.get("queue"),d->{
        page("Severity & patient queue","Staff assess severity. Arrival time breaks ties within the same priority.",this::queue);content.getChildren().add(label("Default order: Emergency → Urgent → Routine. Administrators may configure the order. Refresh to see other users’ changes.","notice"));
        var t=table(d,"Patient:patientName","Appointment:appointmentId","Severity:severityLevel","Arrival:arrivalTime","Waiting minutes:waitingMinutes","State:queueStatus");
        var actions=row(primary("Assess severity",()->selection(t,r->{var f=new Form();f.choice("Severity","severity",List.of("EMERGENCY","URGENT","ROUTINE"),text(r,"severityLevel"));f.area("Reason for assessment","reason","");form("Assess "+text(r,"patientName"),f,"Save assessment",b->{b.put("appointmentId",text(r,"appointmentId"));return client.post("queue",b);},x->queue(),false);})),button("Refresh",this::queue));
        if(role.equals("DOCTOR"))actions.getChildren().addAll(button("Start / continue consultation",()->selection(t,this::beginConsultation)),button("Patient history",()->selection(t,r->records(text(r,"patientId")))));
        content.getChildren().addAll(actions,t);
    });}
    private void beginConsultation(JsonNode visit){
        String appointment=visit.has("appointmentId")?text(visit,"appointmentId"):id(visit),patient=text(visit,"patientId"),state=visit.has("queueStatus")?text(visit,"queueStatus"):text(visit,"status");
        if(!Set.of("WAITING","CHECKED_IN","IN_PROGRESS").contains(state)){message("Check in first","Check in this appointment before starting a consultation.");return;}
        load("Opening consultation…",()->{if(!state.equals("IN_PROGRESS"))client.post("appointments/"+appointment+"/status",Map.of("status","IN_PROGRESS"));return client.get("records?patientId="+patient);},r->{
            var draft=rows(r.path("consultations")).stream().filter(c->text(c,"appointmentId").equals(appointment)).findFirst().orElse(JsonNodeFactory.instance.objectNode());var f=new Form();f.box.getChildren().add(label("Allergies: "+text(r.path("patient"),"allergies")+"\nMedications: "+text(r.path("patient"),"medications"),"notice"));
            f.box.getChildren().add(button("View previous records",()->showRecords(r)));
            f.area("Clinical notes","clinicalNotes",text(draft,"clinicalNotes"));f.area("Diagnosis","diagnosis",text(draft,"diagnosis"));f.area("Prescription and instructions","prescription",text(draft,"prescription"));String follow=text(draft,"followUpDate");f.date("Follow-up (optional)","followUpDate",follow.isBlank()?null:LocalDate.parse(follow));f.choice("Save action","saveAction",List.of("Save draft","Complete visit"),"Save draft");
            form("Consultation • "+text(r.path("patient"),"name"),f,"Save consultation",b->{b.put("appointmentId",appointment);b.put("complete",b.remove("saveAction").equals("Complete visit"));return client.post("consultations",b);},x->queue(),false);
        });
    }
    private void choosePatient(Consumer<String> action){load("Loading patients in your schedule…",()->client.get("appointments?pageSize=100"),d->{var choices=new LinkedHashMap<String,String>();for(var r:d)choices.put(text(r,"patientName")+" (#"+text(r,"patientId")+")",text(r,"patientId"));var dialog=new ChoiceDialog<String>(null,choices.keySet());dialog.initOwner(stage);dialog.setHeaderText("Choose a patient from your recent appointments");dialog.showAndWait().ifPresent(s->action.accept(choices.get(s)));});}
    private void records(String patient){load("Loading medical records…",()->client.get("records"+(patient==null?"":"?patientId="+patient)),d->{page("Medical records • "+text(d.path("patient"),"name"),"Records are visible only to their patient and authorized treating doctors.",()->records(patient));var t=table(d.path("consultations"),"Date:date","Doctor:doctorName","Diagnosis:diagnosis","Follow-up:followUpDate");content.getChildren().addAll(row(button("Open record",()->selection(t,this::recordDetails)),button("Refresh",refresh)),label("Allergies: "+text(d.path("patient"),"allergies")+"    Medications: "+text(d.path("patient"),"medications"),"notice"),t);});}
    private void showRecords(JsonNode data){var t=table(data.path("consultations"),"Date:date","Doctor:doctorName","Diagnosis:diagnosis");var box=new VBox(12,button("Open selected record",()->selection(t,this::recordDetails)),t);show("Previous consultations",box);}
    private void recordDetails(JsonNode r){var area=new TextArea("Date: "+text(r,"date")+"\nDoctor: "+text(r,"doctorName")+"\n\nClinical notes\n"+text(r,"clinicalNotes")+"\n\nDiagnosis\n"+text(r,"diagnosis")+"\n\nPrescription\n"+text(r,"prescription")+"\n\nFollow-up: "+text(r,"followUpDate"));area.setEditable(false);area.setWrapText(true);show("Consultation record",area);}
    private void documents(int page,String search,String patient){load("Loading documents…",()->client.get("documents?pageSize=25&page="+page+"&q="+query(search)+(patient.isBlank()?"":"&patientId="+query(patient))),d->{
        page("Medical documents","PDF uploads, text search and access-controlled downloads.",()->documents(page,search,patient));var q=new TextField(search);q.setPromptText("Search title or extracted text");var p=new TextField(patient);p.setPromptText("Patient ID (optional)");var t=table(d,"Title:title","Patient ID:patientId","Category:documentType","Uploaded:uploadedAt","Bytes:fileSize");var actions=new FlowPane(8,8);actions.getChildren().addAll(q,button("Search",()->documents(1,q.getText(),p.getText())),button("Refresh",refresh));if(!role.equals("PATIENT"))actions.getChildren().add(p);
        if(!role.equals("ADMIN"))actions.getChildren().addAll(primary("Upload PDF",()->upload(patient)),button("Save PDF",()->selection(t,r->saveFile("documents/"+text(r,"documentId")+"?download=true","document-"+text(r,"documentId")+".pdf"))),button("Extracted text",()->selection(t,r->{var a=new TextArea(text(r,"extractedText"));a.setEditable(false);a.setWrapText(true);show("Extracted PDF text",a);})),button("Archive",()->selection(t,r->confirm("Archive this document?",()->mutate("documents/"+text(r,"documentId")+"/archive",Map.of())))));
        content.getChildren().addAll(actions,t,paging(page,d.size(),n->documents(n,search,patient)));
    });}
    private void upload(String patient){var chooser=new FileChooser();chooser.setTitle("Choose a PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF documents","*.pdf"));var file=chooser.showOpenDialog(stage);if(file==null)return;var f=new Form();f.box.getChildren().add(new Label(file.getName()));f.input("Title","title",file.getName());f.area("Description","description","");f.choice("Category","category",List.of("LAB_REPORT","PRESCRIPTION","MEDICAL_HISTORY","REFERRAL","OTHER"),"LAB_REPORT");if(role.equals("DOCTOR"))f.input("Patient ID","patientId",patient);form("Upload PDF",f,"Upload",b->{var fields=new LinkedHashMap<String,String>();b.forEach((k,v)->fields.put(k,v.toString()));return client.upload(file.toPath(),fields);},r->refresh.run(),false);}
    private void saveFile(String resource,String filename){var chooser=new FileChooser();chooser.setInitialFileName(filename);var file=chooser.showSaveDialog(stage);if(file!=null)load("Downloading…",()->{Files.write(file.toPath(),client.download(resource));return file;},r->message("Saved",r.getAbsolutePath()));}
    private void availability(){load("Loading working hours…",()->client.get("availability"),d->{
        page("Your availability","Set weekly hours before patients can book. Day 1 is Monday; day 7 is Sunday.",this::availability);
        content.getChildren().addAll(row(primary("Set day’s hours",()->{var f=new Form();f.choice("Day (Monday=1)","dayOfWeek",List.of("1","2","3","4","5","6","7"),"1");f.input("Start time (HH:mm)","startTime","09:00");f.input("End time (HH:mm)","endTime","17:00");f.input("Visit duration (minutes)","appointmentDuration",text(d,"appointmentDuration"));form("Weekly hours",f,"Save hours",b->client.post("availability",b),r->availability(),false);}),button("Block time off",()->{var f=new Form();f.input("Start (YYYY-MM-DDTHH:mm)","start",LocalDate.now().plusDays(1)+"T12:00");f.input("End (YYYY-MM-DDTHH:mm)","end",LocalDate.now().plusDays(1)+"T13:00");f.input("Reason","reason","");form("Block unavailable time",f,"Save",b->client.post("unavailability",b),r->availability(),false);})),table(d.path("weekly"),"Day:dayOfWeek","Start:startTime","End:endTime"),label("Upcoming time off","subtitle"),table(d.path("unavailability"),"Start:start","End:end","Reason:reason"));
    });}
    private void users(int page,String search){load("Loading accounts…",()->client.get("users?pageSize=25&page="+page+"&q="+query(search)),d->{
        page("User management","Administrators create doctor accounts. Patients can also register themselves.",()->users(page,search));var q=new TextField(search);var t=table(d,"ID:id","Name:name","Email:email","Role:role","Status:status","Patient ID:patientId","Specialty:specialization");var actions=new FlowPane(8,8);actions.getChildren().addAll(q,button("Search",()->users(1,q.getText())),primary("Create account",()->{var f=new Form();f.input("Full name","name","");f.input("Email address","email","");f.password("Initial password (10+ characters)","password");f.choice("Role","role",List.of("DOCTOR","PATIENT"),"DOCTOR");f.input("Specialty (doctor)","specialization","");f.input("Consultation fee","consultationFee","0");form("Create clinic account",f,"Create",b->client.post("users",b),r->refresh.run(),false);}),button("Edit doctor",()->selection(t,r->{if(!text(r,"role").equals("DOCTOR")){message("Choose a doctor","Only doctors have specialty and fee settings.");return;}var f=new Form();f.input("Specialty","specialization",text(r,"specialization"));f.input("Fee","consultationFee",text(r,"consultationFee"));f.area("Profile description","profileDescription",text(r,"profileDescription"));form("Edit doctor",f,"Save",b->client.post("users/"+id(r)+"/doctor",b),x->refresh.run(),false);})),button("Activate / deactivate",()->selection(t,r->confirm("Change account status for "+text(r,"name")+"?",()->mutate("users/"+id(r)+"/status",Map.of("status",text(r,"status").equals("ACTIVE")?"INACTIVE":"ACTIVE"))))),button("Create reset link",()->selection(t,r->confirm("Create a one-time reset link for "+text(r,"name")+"?",()->load("Creating reset link…",()->client.post("users/"+id(r)+"/reset",Map.of()),x->{var a=new TextArea(text(x,"resetUrl"));a.setEditable(false);a.setWrapText(true);show("Share privately with the account holder • one use",a);})))));
        content.getChildren().addAll(actions,t,paging(page,d.size(),p->users(p,search)));
    });}
    private void reports(LocalDate from,LocalDate to){String params="from="+from+"&to="+to;load("Loading reports…",()->client.get("reports?"+params),d->{page("Reports & analytics","Counts are calculated from the live MySQL database.",()->reports(from,to));var a=new DatePicker(from);var b=new DatePicker(to);content.getChildren().add(row(a,b,primary("Apply dates",()->{if(a.getValue()!=null&&b.getValue()!=null)reports(a.getValue(),b.getValue());}),button("Export CSV",()->saveFile("reports?"+params+"&format=csv","clinic-report.csv"))));var tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);for(String key:List.of("daily","status","workload","registrations","specialties")){String field=switch(key){case "status"->"status";case "workload"->"doctor";case "specialties"->"specialty";default->"date";};var axis=new CategoryAxis();var count=new NumberAxis();count.setLabel("Count");var chart=new BarChart<String,Number>(axis,count);chart.setLegendVisible(false);var series=new XYChart.Series<String,Number>();for(var item:d.path(key))series.getData().add(new XYChart.Data<>(text(item,field),item.path("count").asInt()));chart.getData().add(series);tabs.getTabs().add(new Tab(key,new VBox(12,chart,table(d.path(key),"Label:"+field,"Count:count"))));}VBox.setVgrow(tabs,Priority.ALWAYS);content.getChildren().add(tabs);});}
    private void settings(){load("Loading settings…",()->client.get("settings"),d->{page("Clinic settings","Changes are validated against existing bookings.",this::settings);var f=new Form();for(String key:List.of("clinic_name","cancellation_hours","booking_days","reminder_hours","appointment_duration","working_start","working_end","triage_priority_order"))f.input(key.replace('_',' '),key,text(d,key));f.input("New specialty (optional)","newSpecialty","");content.getChildren().addAll(scroll(f.box),primary("Save settings",()->mutate("settings",f.values())));});}
    private void audit(int page){load("Loading audit log…",()->client.get("audit?pageSize=25&page="+page),d->{page("Audit log","Recorded administrative and clinical actions.",()->audit(page));content.getChildren().addAll(table(d,"Actor:actor","Action:action","Type:entityType","ID:entityId","When:createdAt"),paging(page,d.size(),this::audit));});}
    private void notifications(){load("Loading notifications…",()->client.get("notifications"),d->{page("Notifications","Appointment reminders and clinic updates.",this::notifications);var t=table(d,"Title:title","Message:message","Read:readStatus","When:createdAt");content.getChildren().addAll(row(button("Mark all read",()->mutate("notifications",Map.of())),button("Read selected",()->selection(t,r->message(text(r,"title"),text(r,"message")))),button("Refresh",refresh)),t);});}
    private void profile(){load("Loading profile…",()->client.get("profile"),d->{page("Profile & password",text(d,"email")+" • "+text(d,"role"),this::profile);var f=new Form();f.input("Full name","name",text(d,"name"));if(role.equals("PATIENT")){for(String key:List.of("dateOfBirth","bloodGroup","emergencyContact","allergies","medications"))f.input(key,key,text(d,key));}content.getChildren().addAll(scroll(f.box),row(primary("Save profile",()->mutate("profile",f.values())),button("Change password",()->{var password=new Form();password.password("Current password","currentPassword");password.password("New password (10+ characters)","newPassword");password.password("Confirm new password","confirmPassword");form("Change password",password,"Change and sign out",b->{if(!b.get("newPassword").equals(b.remove("confirmPassword")))throw new IllegalArgumentException("New passwords do not match.");return client.post("password",b);},r->login(false),false);})));});}
    private void showTable(String title,JsonNode data,String... columns){show(title,table(data,columns));}
    private void show(String title,Node body){var dialog=new Dialog<Void>();dialog.initOwner(stage);dialog.setTitle(title);dialog.getDialogPane().getStylesheets().add(css);dialog.getDialogPane().setContent(body);dialog.getDialogPane().setPrefSize(840,600);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.setResizable(true);dialog.showAndWait();}
    @FunctionalInterface private interface Submit{JsonNode run(Map<String,Object> values)throws Exception;}
    private void form(String title,Form form,String submit,Submit work,Consumer<JsonNode> done,boolean review){
        var dialog=new Dialog<Void>();dialog.initOwner(stage);dialog.setTitle(title);dialog.setHeaderText(title);dialog.getDialogPane().getStylesheets().add(css);dialog.getDialogPane().setContent(scroll(form.box));dialog.getDialogPane().setPrefSize(620,650);dialog.setResizable(true);var save=new ButtonType(submit,ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,save);var button=(Button)dialog.getDialogPane().lookupButton(save);button.getStyleClass().add("primary");
        button.addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume();var values=form.values();if(review){String details=values.entrySet().stream().map(e->e.getKey()+": "+e.getValue()).collect(java.util.stream.Collectors.joining("\n"));var a=new Alert(Alert.AlertType.CONFIRMATION,details,ButtonType.CANCEL,ButtonType.OK);a.initOwner(dialog.getDialogPane().getScene().getWindow());a.setHeaderText("Confirm appointment details");if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;}
            dialog.getDialogPane().setDisable(true);dialog.setOnCloseRequest(e->e.consume());status.setText("Saving…");
            worker.submit(()->{try{var result=work.run(values);Platform.runLater(()->{dialog.getDialogPane().setDisable(false);dialog.setOnCloseRequest(null);dialog.close();done.accept(result);});}catch(Exception e){Platform.runLater(()->{dialog.getDialogPane().setDisable(false);dialog.setOnCloseRequest(null);error(e);});}});
        });dialog.showAndWait();
    }
    private final class Form {
        final VBox box=new VBox(12);final Map<String,Supplier<Object>> fields=new LinkedHashMap<>();
        Form(){box.setPadding(new Insets(12));}
        void add(String label,Control control){control.setMaxWidth(Double.MAX_VALUE);var caption=new Label(label);caption.setLabelFor(control);box.getChildren().add(new VBox(5,caption,control));}
        TextField input(String label,String key,String value){var t=new TextField(value);t.setId(key);fields.put(key,t::getText);add(label,t);return t;}
        void password(String label,String key){var p=new PasswordField();p.setId(key);fields.put(key,p::getText);add(label,p);}
        void area(String label,String key,String value){var t=new TextArea(value);t.setWrapText(true);t.setPrefRowCount(3);t.setId(key);fields.put(key,t::getText);add(label,t);}
        DatePicker date(String label,String key,LocalDate value){var p=new DatePicker(value);p.setEditable(false);p.setId(key);fields.put(key,()->Objects.toString(p.getValue(),""));add(label,p);return p;}
        ComboBox<String> choice(String label,String key,List<String> options,String value){var c=new ComboBox<String>(FXCollections.observableArrayList(options));if(options.contains(value))c.setValue(value);c.setId(key);fields.put(key,()->Objects.toString(c.getValue(),""));add(label,c);return c;}
        Map<String,Object> values(){var values=new LinkedHashMap<String,Object>();fields.forEach((key,value)->values.put(key,value.get()));return values;}
    }
}
