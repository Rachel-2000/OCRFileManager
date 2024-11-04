package fulltext.print.demo.controller;

import fulltext.print.demo.service.DocumentService;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;

@RestController
public class UploadController {
    // controller for file upload
    //     TODO:

    @Autowired
    private DocumentService documentService;

    @RequestMapping("/upload")
    public ModelAndView upload(@RequestParam(name = "file") MultipartFile file) throws InterruptedException, ExecutionException, IOException, ParseException {

        String path = "D:\\";
        File f = new File(path,"test.csv");
        file.transferTo(f);
        documentService.insertFile(f);
        System.out.println("Upload successfully");
        f.delete();
        return new ModelAndView("redirect:/search");
    }

}