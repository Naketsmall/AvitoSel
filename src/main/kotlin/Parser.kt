import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import java.nio.charset.StandardCharsets.UTF_8


class Parser(private val link: String, private val hook: String,
             private var driver: ChromeDriver): Runnable {
    private var running: Boolean = false
    private var checked: ArrayDeque<String> = ArrayDeque(listOf("", "", "", "", "", "", ""))
    private val SLEEP_CONST: Long = 10000

    init {
        driver.get(link)
        println("initialized")
        fillChecked()
    }

    private fun fillChecked(){
        val cur = driver.findElementsByClassName("iva-item-content-rejJg")
        for (el in 0 until checked.size){
            checked.addLast(cur[el].findElement(By.xpath(".//div[1]/a")).getAttribute("href"))
            checked.removeFirst()
            println(cur[el].findElement(By.xpath(".//div[1]/a")).getAttribute("href"))
        }
        Thread.sleep(SLEEP_CONST)
    }

    fun checkFeed(driver: ChromeDriver){
        try {
            driver.get(link)
            parseElements()
        } catch (e: HttpStatusException) {
            e.printStackTrace()
        } finally {
            Thread.sleep(SLEEP_CONST)
        }

    }

    private fun parseElements() {
        val cur = driver.findElementsByClassName("iva-item-content-rejJg")
        for (el in 0 until checked.size){
            try {
                val path = cur[el].findElement(By.xpath(".//div[1]/a")).getAttribute("href")
                if (path !in checked) {
                    println("\n" + path)
                    sendMessage(cur[el])
                    checked.removeLast()
                    checked.addFirst(path)
                }
            } catch (e: java.lang.IndexOutOfBoundsException) {
                e.printStackTrace()
                break
            }
        }
        print(".")
    }

    override fun run() {
        running = true
        //fillChecked()
        Thread.sleep(SLEEP_CONST)
        while (running) {
            try {
                driver.navigate().refresh()
                parseElements()
            } catch (e: HttpStatusException) {
                e.printStackTrace()
                continue
            } finally {
                Thread.sleep(SLEEP_CONST)
            }

        }

    }

    private fun sendMessage(el: WebElement) {

        val title = try {el.findElement(By.xpath(".//div[2]/div[2]/a")).getAttribute("title")}
        catch (e: org.openqa.selenium.NoSuchElementException) {""}

        val url = try {el.findElement(By.xpath(".//div[1]/a")).getAttribute("href")}
        catch (e: org.openqa.selenium.NoSuchElementException) {""}

        val price = try {el.findElement(By.xpath(".//div[2]/div[3]/span/span[1]/span")).text}
        catch (e: org.openqa.selenium.NoSuchElementException) {""}

        var descr = try {el.findElement(By.xpath(".//div[2]/div[5]/div")).text.replace(""""""", "").replace("\n", " ")}
        catch (e: org.openqa.selenium.NoSuchElementException) {""}


        if (descr.length > 800)
            descr = descr.substring(0, 800) + "..."

        val imUrl = try {el.findElement(By.xpath(".//div[1]/a/div/div/ul/li/div/img"))
            .getAttribute("srcset").split(',').last().split(' ').first()}
        catch (e: org.openqa.selenium.NoSuchElementException) {""}

        val req = Message(title, price, descr, url, imUrl).build()
        println(req)
        try {
            println(
                Jsoup.connect(hook).header("Content-Type", "application/json").requestBody(req)
                    .method(Connection.Method.POST).execute().statusCode()
            )
        } catch (e: HttpStatusException) {
            e.printStackTrace()
        }

    }

    private class Message(
        var title: String,
        var price: String,
        var description: String,
        var url: String,
        var imUrl: String,
        var color: Int = 10828031
    ) {

        fun build(): String {
            return String("""
                {
                    "content": "Нашел новое предложение:",
                    "embeds": [{
                            "title": "$title \nЦена: $price",
                            "color": $color,
                            "description": "$description",
                            "url": "$url",
                            "image": {"url": "$imUrl"}}]
                }""".toByteArray(), UTF_8)
        }
    }
}
