// Native scene-graph smoke test. Requires an isolated test backend, a graphical session,
// and the CLINIC_ADMIN_EMAIL / CLINIC_ADMIN_PASSWORD environment variables.
import com.clinic.desktop.ClinicApp;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class DesktopUiSmoke extends Application {
    private ClinicApp app;private Stage stage;
    public static void main(String[] args){launch(args);}
    @Override public void start(Stage stage)throws Exception{
        this.stage=stage;app=new ClinicApp();app.start(stage);
        Thread thread=new Thread(()->{try{exercise();System.out.println("PASS native JavaFX controls: admin creates doctor, doctor sets availability, patient registers and books; all role pages render.");fx(()->{app.stop();Platform.exit();return null;});}catch(Throwable e){e.printStackTrace();try{snapshot("failure");}catch(Exception ignored){}System.exit(1);}},"native-ui-smoke");thread.setDaemon(true);thread.start();
    }
    private <T>T fx(Callable<T> task)throws Exception{var future=new CompletableFuture<T>();Platform.runLater(()->{try{future.complete(task.call());}catch(Throwable e){future.completeExceptionally(e);}});return future.get(10,TimeUnit.SECONDS);}
    private List<Node> nodes(String selector){var result=new ArrayList<Node>();for(var window:Window.getWindows())if(window.isShowing()&&window.getScene()!=null)result.addAll(window.getScene().getRoot().lookupAll(selector));return result;}
    private void click(String caption)throws Exception{waitFor(()->nodes(".button").stream().anyMatch(n->n instanceof Button b&&b.getText().equals(caption)&&!b.isDisabled()));fx(()->{var b=nodes(".button").stream().filter(n->n instanceof Button v&&v.getText().equals(caption)&&!v.isDisabled()).map(n->(Button)n).findFirst().orElseThrow(()->new AssertionError("Missing enabled button: "+caption));Platform.runLater(b::fire);return null;});}
    private void input(String key,String value)throws Exception{waitFor(()->!nodes("#"+key).isEmpty());fx(()->{((TextInputControl)nodes("#"+key).getLast()).setText(value);return null;});}
    @SuppressWarnings("unchecked") private void choose(String key,String value)throws Exception{fx(()->{((ComboBox<String>)nodes("#"+key).getLast()).setValue(value);return null;});}
    private void waitFor(Callable<Boolean> condition)throws Exception{long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<limit){if(fx(condition))return;Thread.sleep(100);}throw new AssertionError("UI wait timed out");}
    private boolean ready(){return !stage.getScene().getRoot().isDisabled()&&Window.getWindows().size()==1;}
    private void page(String title)throws Exception{waitFor(()->ready()&&stage.getScene().getRoot().lookupAll(".title").stream().anyMatch(n->n instanceof Label l&&l.getText().startsWith(title)));}
    private void login(String email,String password)throws Exception{input("server",System.getenv("APP_BASE_URL"));input("email",email);input("password",password);click("Sign in");page("Hello");}
    private void snapshot(String name)throws Exception{fx(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var image=stage.getScene().snapshot(null);var out=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,image.getPixelReader().getArgb(x,y));Files.createDirectories(Path.of("docs/screenshots-javafx"));ImageIO.write(out,"png",Path.of("docs/screenshots-javafx",name+".png").toFile());return null;});}
    @SuppressWarnings("unchecked") private void select(String field,String value)throws Exception{fx(()->{var table=(TableView<JsonNode>)stage.getScene().getRoot().lookup(".table-view");var item=table.getItems().stream().filter(r->r.path(field).asText().equals(value)).findFirst().orElseThrow();table.getSelectionModel().select(item);return null;});}
    private void exercise()throws Exception{
        String suffix=UUID.randomUUID().toString().substring(0,8),doctor="ui-doctor-"+suffix+"@example.test",patient="ui-patient-"+suffix+"@example.test",password="UiTest!"+UUID.randomUUID(),name="UI Doctor "+suffix;
        snapshot("login");login(System.getenv("CLINIC_ADMIN_EMAIL"),System.getenv("CLINIC_ADMIN_PASSWORD"));snapshot("admin-overview");
        click("User management");page("User management");click("Create account");input("name",name);input("email",doctor);input("password",password);input("specialization","General Medicine");input("consultationFee","450");click("Create");page("User management");
        click("Reports & analytics");page("Reports");snapshot("reports");click("Clinic settings");page("Clinic settings");click("Audit log");page("Audit log");click("Sign out");waitFor(this::ready);
        login(doctor,password);click("Availability");page("Your availability");click("Set day’s hours");choose("dayOfWeek",Integer.toString(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1).getDayOfWeek().getValue()));click("Save hours");page("Your availability");click("Set day’s hours");choose("dayOfWeek",Integer.toString(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getDayOfWeek().getValue()));click("Save hours");page("Your availability");snapshot("doctor-availability");click("Severity & patient queue");page("Severity");snapshot("doctor-queue");click("Sign out");waitFor(this::ready);
        click("New patient? Create an account");input("server",System.getenv("APP_BASE_URL"));input("name","UI Patient "+suffix);input("email",patient);input("password",password);input("confirmPassword",password);click("Create account");page("Hello");click("Find a doctor");page("Find a doctor");select("name",name);click("Book with selected doctor");waitFor(()->!nodes("#date").isEmpty());fx(()->{((DatePicker)nodes("#date").getLast()).setValue(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));return null;});click("Load available times");waitFor(()->!((ComboBox<?>)nodes("#startTime").getLast()).getItems().isEmpty());fx(()->{((ComboBox<?>)nodes("#startTime").getLast()).getSelectionModel().selectFirst();return null;});input("reason","Native JavaFX UI verification");click("Review and confirm");click("OK");page("Appointments");snapshot("patient-appointments");
        click("Medical records");page("Medical records");click("Documents");page("Medical documents");click("Notifications");page("Notifications");click("Profile & password");page("Profile");snapshot("patient-profile");click("Sign out");waitFor(this::ready);
        login(doctor,password);click("Appointments");page("Appointments");snapshot("doctor-shared-appointment");select("patientName","UI Patient "+suffix);click("Check in");page("Appointments");click("Severity & patient queue");page("Severity");select("patientName","UI Patient "+suffix);click("Assess severity");choose("severity","EMERGENCY");input("reason","Fictional native UI severity check");click("Save assessment");page("Severity");snapshot("doctor-queue");select("patientName","UI Patient "+suffix);click("Start / continue consultation");input("clinicalNotes","Native UI draft persists");input("diagnosis","Fictional demonstration");click("Save consultation");page("Severity");select("patientName","UI Patient "+suffix);click("Start / continue consultation");waitFor(()->!nodes("#clinicalNotes").isEmpty());if(!fx(()->((TextInputControl)nodes("#clinicalNotes").getLast()).getText()).equals("Native UI draft persists"))throw new AssertionError("Draft not restored");choose("saveAction","Complete visit");click("Save consultation");page("Severity");click("Sign out");waitFor(this::ready);
    }
}
