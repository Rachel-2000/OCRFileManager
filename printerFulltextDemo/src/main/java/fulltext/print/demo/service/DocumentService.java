package fulltext.print.demo.service;

import fulltext.print.demo.bean.Document;
import fulltext.print.demo.bean.SearchResult;
import fulltext.print.demo.dao.document.DocumentDao;
import lombok.extern.log4j.Log4j2;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

//9。22
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVParser;
//import org.apache.commons.csv.CSVPrinter;
//import org.apache.commons.csv.CSVRecord;

//10.5
import java.nio.charset.Charset;
import com.csvreader.CsvReader;

@Log4j2
@Service
public class DocumentService {

    @Autowired
    private SolrClient solrClient;

    @Autowired
    private DocumentDao documentDao;

    @Autowired
    private OCRService ocrService;

    @Value("${spring.data.solr.core}")
    private String collection;

    @Value("${spring.data.solr.keepDays}")
    private int keepDays;

    public SearchResult search(String queryString, String time, int page, int row)  throws Exception {

        log.info("Start searching " + queryString + " page: " + page + " row: " + row);
        SearchResult searchResult = new SearchResult();
        SolrQuery solrQuery = new SolrQuery(); //创建一个查询对象

        // set query
        solrQuery.setQuery(queryString); //设置查询条件（querystring应该是input？）
        // set fq
//        solrQuery.setFilterQueries("printTime:[2019-11-26T14:02:01Z TO 2019-11-26T14:02:24Z]");
        if (time.equals("5 MONTHS")) {
            solrQuery.setFilterQueries("printTime:[NOW-5MONTHS TO NOW]");
        } else {
            solrQuery.setFilterQueries("printTime:[* TO NOW]");
        }
        // set page
        solrQuery.setStart(page * row); //设置分页
        solrQuery.setRows(row);
        // set highlighting 设置高亮显示
        solrQuery.setHighlight(true);
        solrQuery.addHighlightField("title");
        solrQuery.addHighlightField("author");
        solrQuery.addHighlightField("content");
        solrQuery.setHighlightSimplePre("<b style='color:red'>");
        solrQuery.setHighlightSimplePost("</b>");
        // sort by print time
        solrQuery.setSort("printTime", SolrQuery.ORDER.desc);
        // select corresponding text
        solrQuery.setHighlightFragsize(100);

        QueryResponse response = solrClient.query(collection, solrQuery); //执行查询
        SolrDocumentList results = response.getResults(); //取查询结果
        List<Document> documents = new ArrayList<>();
        Map<String, Map<String, List<String>>> highlightResult = response.getHighlighting();

        for (SolrDocument document : results) {
            String id = (String) document.get("id");
            if (highlightResult.get(id) != null && highlightResult.get(id).get("title") != null) {
                document.setField("title", highlightResult.get(id).get("title").get(0));
            }
            if (highlightResult.get(id) != null && highlightResult.get(id).get("content") != null) {
                document.setField("content", highlightResult.get(id).get("content").get(0));
            }

            if (highlightResult.get(id) != null && highlightResult.get(id).get("author") != null) {
                document.setField("author", highlightResult.get(id).get("author").get(0));
            }
            if (highlightResult.get(id) != null && highlightResult.get(id).get("url") != null) {
                document.setField("url", highlightResult.get(id).get("url").get(0));
            }


            Document doc = new Document();
            doc.setId(id);
            doc.setTitle((String) document.get("title"));
            doc.setAuthor((String) document.get("author"));
            doc.setUrl((String) document.get("url"));
            if ( document.get("content")!= null && ((String) document.get("content")).length() > 500) { // Solr has some bug here. now is ok!
                document.setField("content", "Click view to see the detail");
            }
            doc.setContent((String) document.get("content"));
            doc.setPrintTime((Date) document.get("printTime"));
            documents.add(doc);
        }

        searchResult.setRecordCount(results.getNumFound());
        searchResult.setDocumentList(documents);
        searchResult.setCurPage(page);

        long recordCount = searchResult.getRecordCount();
        long pageCount = recordCount / row;

        if (recordCount % row > 0) {
            pageCount += 1;
        }

        searchResult.setPageCount(pageCount);

        log.info("Search finished");

        return searchResult;
    }

    public void insertDocument(Document document) {
        documentDao.insertDocument(document);
    }

    public void deleteOldDocuments() {
        log.info("Document Service: Start deleting old documents");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -keepDays);
        Date deleteTime = calendar.getTime();
        documentDao.deleteDocumentBeforeTime(deleteTime);
        log.info("Document Service: Delete succeed!");
    }

    public void deleteAll() {
        log.info("Document Service: Start deleting all documents");
        Date deleteTime = new Date();
        documentDao.deleteDocumentBeforeTime(deleteTime);
        log.info("Document Service: Delete succeed!");
    }

    public boolean insertFile(File file) throws IOException, ExecutionException, InterruptedException, ParseException {
        String content = "";
        if (isImage(file)) {
            content = ocrService.doOCRForOneFile(file);
        }
        else if (isCsv(file)) {
            ArrayList<String[]> csvList;
            csvList = readCsv(file);

//            SimpleDateFormat df= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            for (int row=0;row<csvList.size();row++){
                Document document = new Document();
////                document.setId(UUID.randomUUID().toString());
                document.setId(Integer.toString(row)); // id starts from 0
                document.setAuthor(csvList.get(row)[0]);
                document.setTitle(csvList.get(row)[1]);
                document.setContent(csvList.get(row)[2]);
                document.setUrl(csvList.get(row)[3]);
//                document.setPrintTime(csvList.get(row)[4]);
//                document.setPrintTime(df.parse(String.valueOf(new Date())));
//                System.out.print(csvList.get(row)[0]+" ");
                if (row==0) System.out.println(document.toString()); // for test
                insertDocument(document);
            }
            return true;

        } else {
            System.out.println(file.getName().substring(file.getName().lastIndexOf(".")));
            File filename = new File(String.valueOf(file));
            InputStreamReader reader = new InputStreamReader(new FileInputStream(filename));
            BufferedReader br = new BufferedReader(reader);

            String line = br.readLine();
            while (line != null) {
                content = content + line;
                line = br.readLine();
            }
            br.close();
        }
//

//        document.setId(UUID.randomUUID().toString());
//        document.setAuthor("作者");
//        document.setTitle(file.getName());
//        document.setContent(content);
//        document.setPrintTime(new Date());
//        document.setUrl(file.getPath());
//
//        insertDocument(document);

        return true;
    }

    public boolean insertFile(String filePath) throws IOException, ExecutionException, InterruptedException, ParseException {
        File file = new File(filePath);
        return insertFile(file);
    }

    @Async
    public Future<String> insertFileAsync(String filePath) throws IOException, ExecutionException, InterruptedException, ParseException {
        File file = new File(filePath);
        boolean result = insertFile(file);
        if (result) {
            return new AsyncResult<>("Complete");
        } else {
            return new AsyncResult<>("Failed");
        }
    }

    @Async
    public Future<String> insertFileAsync(File file) throws IOException, ExecutionException, InterruptedException, ParseException {
        boolean result = insertFile(file);
        if (result) {
            return new AsyncResult<>("Complete");
        } else {
            return new AsyncResult<>("Failed");
        }
    }

    private boolean isImage(File file) {
        // Judge whether the file is image
        try {
            Image image = ImageIO.read(file);
            return image != null;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isCsv(File file) {
        return file.getName().substring(file.getName().lastIndexOf(".")).equals(".csv");
    }

    private boolean isTxt(File file) {
        return file.getName().substring(file.getName().lastIndexOf(".")).equals(".txt");
    }

    private boolean isZip(File file) {
        return file.getName().substring(file.getName().lastIndexOf(".")).equals(".zip");
    }

    private ArrayList<String[]> readCsv(File file) {
        ArrayList<String[]> csvList = new ArrayList<String[]>();
        try{
            CsvReader reader = new CsvReader(String.valueOf(file));
            reader.readHeaders();
            while (reader.readRecord()){
                csvList.add(reader.getValues());
            }
            reader.close();
            for (int row=0;row<csvList.size();row++){
                System.out.print(csvList.get(row)[0]+" "+csvList.get(row)[1]);
            } // 测试打印

        } catch (Exception e) {
            e.printStackTrace();
        }
        return csvList;
    }

    public List<Document> selectAll() {
        return documentDao.findAll();
    }

    public Document selectDocumentById(String id) {
        return documentDao.findDocumentById(id);
    }

    public void commitToSolr() {
        documentDao.commitSolr();
    }



}
