package com.tightdb.typed;

import static org.testng.AssertJUnit.fail;

import java.io.File;

import org.testng.annotations.DataProvider;
//import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.tightdb.ReadTransaction;
import com.tightdb.SharedGroup;
import com.tightdb.SharedGroup.Durability;
import com.tightdb.WriteTransaction;

public class SharedGroupTest {

    protected SharedGroup db;

    protected String testFile = "transact.tightdb";

    protected void deleteFile(String filename) {
        File f = new File(filename);
        if (f.exists())
            f.delete();
        f = new File(filename + ".lock");
        if (f.exists())
            f.delete();
    }

    public void init(Durability durability) {
        deleteFile(testFile);
        db = new SharedGroup(testFile, durability);
    }

    public void clear() {
        db.close();
        deleteFile(testFile);
    }




    @Test 
    public void testExistingLockFileWithDeletedDb() {
        String uniqueName = "test991UniqueName.tightdb";

        SharedGroup sg = new SharedGroup(uniqueName);

        WriteTransaction wt = sg.beginWrite();
        try {
            wt.getTable("tableName");
            wt.commit();
        } catch (Throwable t){
            wt.rollback();
        }

        wt = sg.beginWrite();
        wt.getTable("tableName");
        // Do not end the write transaction - leaving the .lock file there
        // Delete tightdb file, but NOT .lock file
        new File(uniqueName).delete();

        // If the lock file still exist (which it does until garbage collector has been run)
        if(new File(uniqueName + ".lock").exists()) {
            // Try creating new shared group, while lock file is still there
            try { SharedGroup sg2 = new SharedGroup(uniqueName); fail("The database file is missing, but a .lock file is present."); } catch(com.tightdb.IOException e) { }
        }
    }


    @Test(dataProvider = "durabilityProvider")
    public void expectExceptionWhenMultipleBeginWrite(Durability durability) {
        init(durability);

        WriteTransaction wt = db.beginWrite();
        try {
            db.beginWrite(); //Expect exception. Only 1 beginWrite() is allowed
        } catch (IllegalStateException e){
            wt.rollback();
            clear();
            return;
        }
        assert(false);        
    }

    @Test(dataProvider = "durabilityProvider")
    public void onlyOneReadTransaction(Durability durability) {
        init(durability);

        ReadTransaction rt = db.beginRead();
        try {
            db.beginRead(); // Expect exception. Only 1 begibRead() is allowed
        } catch (IllegalStateException e) {
            rt.endRead();
            clear();
            return;
        }
        assert(false);        
    }

    @Test(dataProvider = "durabilityProvider")
    public void noCloseSharedGroupDuringTransaction(Durability durability) {
        init(durability);

        ReadTransaction rt = db.beginRead();
        try {
            db.close(); // Expect exception. Must not close shared group during active transaction
        } catch (IllegalStateException e){
            rt.endRead();
            clear();
            return;
        }
        assert(false);
    }

    @Test(enabled=false, dataProvider = "durabilityProvider")
    public void fileMustExistParameter(Durability durability) {
        // test not applicable for MEM_ONLY
        if (durability == Durability.MEM_ONLY)
            return;

        String mustExistFile = "mustexistcheck.tightdb";

        // Check that SharedGroup asserts when there is no file
        deleteFile(mustExistFile);
        try {
            db = new SharedGroup(mustExistFile, durability, true);
            assert(false);
        } catch (com.tightdb.IOException e) {
            // expected
        } catch (Exception e) {
            assert(false);
        }
        // Don't expect anything to close due to failure.

        // Create file and see that it can be opened now
        db = new SharedGroup(mustExistFile, durability, false);
        db.close();
        // Then set fileMustExist=true, and it should work
        db = new SharedGroup(mustExistFile, durability, true);
        db.close();

        deleteFile(mustExistFile);
    }

    @Test(dataProvider = "durabilityProvider")
    public void shouldReserve(Durability durability) {
        // test not applicable for MEM_ONLY
        if (durability == Durability.MEM_ONLY)
            return;

        // First create file
        String fileName = "sizefile.tightdb";
        deleteFile(fileName);
        db = new SharedGroup(fileName, durability);
        db.reserve(50012);

        File f = new File(fileName);
        // Not all platforms support this:   assertEquals( 50012, f.length() );
        db.close();

        deleteFile(testFile);
    }


    @DataProvider(name = "durabilityProvider")
    public Object[][] durabilityProvider() {
        return new Object[][] {
                {Durability.FULL},
                {Durability.MEM_ONLY},
                {Durability.ASYNC} };
    }

}
