# Run Instructions — P2P Secure Messenger

Quick steps to build and run the **P2P Secure Messenger** locally.

---

## Prerequisites
- Java JDK 11 or newer installed and `java`/`javac` on PATH  
- Apache Maven installed and `mvn` on PATH

Ports used (make sure not blocked):  
- TCP: `8888` (incoming connections)  
- UDP: `8889` (peer discovery broadcasts)

---

## 1) Clone & build
```bash
# clone (if not already)
git clone https://github.com/Shivaprakash-NP/p2p
cd P2P-Secure-Messenger

# clean & compile
mvn clean install

#run-terminal 1
mvn exec:java -Dexec.mainClass="com.shiva.p2pchat.Main"

#run-terminal 2
mvn exec:java -Dexec.mainClass="com.shiva.p2pchat.Main"
```
