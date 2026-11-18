package com.liquormap.india

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.graphics.Typeface
import android.view.ViewGroup
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var locationManager: LocationManager
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var map: WebView
    private var currentLat = 27.2152
    private var currentLon = 77.5030
    private val radiusMeters = 10_000
    private val shops = mutableListOf<Shop>()

    data class Shop(val name: String, val lat: Double, val lon: Double, val kind: String, val address: String) {
        fun distanceFrom(lat0: Double, lon0: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat - lat0)
            val dLon = Math.toRadians(lon - lon0)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat0)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        buildScreen()
        showMap(emptyList())
        requestLocationAndSearch()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 12); setBackgroundColor(0xFFF5F7FA.toInt()) }
        val title = TextView(this).apply { text = "🍷 LiquorMap India"; textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF15263B.toInt()) }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Nearby licensed shops • आसपास की दुकानें"; textSize = 14f; setTextColor(0xFF536273.toInt()); setPadding(0, 4, 0, 12) })
        val age = TextView(this).apply { text = "18+ only. Follow Rajasthan excise laws and local legal-age requirements.\nकेवल कानूनी उम्र के वयस्कों के लिए। स्थानीय नियमों का पालन करें।"; textSize = 12f; setTextColor(0xFF8A4B00.toInt()); setPadding(10, 8, 10, 8); setBackgroundColor(0xFFFFF1D6.toInt()) }
        root.addView(age)
        val button = Button(this).apply { text = "Use my location & search within 10 km / लोकेशन खोजें"; setOnClickListener { requestLocationAndSearch() } }
        root.addView(button)
        status = TextView(this).apply { text = "Getting location… / लोकेशन प्राप्त हो रही है"; textSize = 13f; setPadding(0, 8, 0, 8); setTextColor(0xFF344054.toInt()) }
        root.addView(status)
        map = WebView(this).apply { settings.javaScriptEnabled = true; webViewClient = WebViewClient() }
        root.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300))
        root.addView(TextView(this).apply { text = "Nearby results / आसपास के परिणाम"; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, 12, 0, 6); setTextColor(0xFF15263B.toInt()) })
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun requestLocationAndSearch() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 41)
            status.text = "Please allow location permission / लोकेशन अनुमति दें"
            return
        }
        try {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) {
                currentLat = last.latitude; currentLon = last.longitude
                status.text = "Searching within 10 km… / 10 किमी में खोज जारी है"
                searchShops()
            } else {
                status.text = "Waiting for GPS location… / GPS लोकेशन का इंतज़ार है"
            }
            val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            locationManager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLat = location.latitude; currentLon = location.longitude; searchShops()
                }
            }, mainLooper)
        } catch (_: Exception) {
            status.text = "Location unavailable. Enable GPS and try again. / लोकेशन नहीं मिली, GPS चालू करें।"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocationAndSearch()
        else status.text = "Location permission is required for nearby search. / आसपास खोजने के लिए अनुमति जरूरी है।"
    }

    private fun searchShops() {
        executor.execute {
            try {
                val query = "[out:json][timeout:25];(node(around:$radiusMeters,$currentLat,$currentLon)[shop~'alcohol|wine'];way(around:$radiusMeters,$currentLat,$currentLon)[shop~'alcohol|wine'];relation(around:$radiusMeters,$currentLat,$currentLon)[shop~'alcohol|wine'];);out center tags;"
                val conn = URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000; conn.setRequestProperty("User-Agent", "LiquorMapIndia/1.0 (Android)")
                val body = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
                val arr = JSONObject(body).optJSONArray("elements") ?: JSONArray()
                val found = mutableListOf<Shop>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i); val tags = item.optJSONObject("tags") ?: JSONObject()
                    val lat = if (item.has("lat")) item.getDouble("lat") else item.optJSONObject("center")?.optDouble("lat") ?: continue
                    val lon = if (item.has("lon")) item.getDouble("lon") else item.optJSONObject("center")?.optDouble("lon") ?: continue
                    val name = tags.optString("name", "Liquor / Wine shop")
                    val kind = tags.optString("shop", "alcohol")
                    val address = listOf("addr:street", "addr:suburb", "addr:city").mapNotNull { k -> tags.optString(k).takeIf { it.isNotBlank() } }.joinToString(", ")
                    found.add(Shop(name, lat, lon, kind, address))
                }
                val sorted = found.distinctBy { "${it.lat},${it.lon}" }.sortedBy { it.distanceFrom(currentLat, currentLon) }
                runOnUiThread { shops.clear(); shops.addAll(sorted); renderResults(); showMap(sorted) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Could not load shop data. Check internet and retry.\nदुकानों का डेटा नहीं मिला; इंटरनेट जाँचें।"; renderResults() }
            }
        }
    }

    private fun renderResults() {
        results.removeAllViews()
        status.text = "${shops.size} mapped result(s) within 10 km / 10 किमी में ${shops.size} परिणाम"
        if (shops.isEmpty()) {
            results.addView(TextView(this).apply { text = "No mapped shops found. Map data may be incomplete; this does not prove that no shops exist.\nमैप पर दुकान नहीं मिली। OpenStreetMap डेटा अधूरा हो सकता है।"; setPadding(8, 12, 8, 12); textSize = 14f })
        }
        shops.forEach { shop ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10); setBackgroundColor(0xFFFFFFFF.toInt()) }
            card.addView(TextView(this).apply { text = shop.name; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF15263B.toInt()) })
            val km = shop.distanceFrom(currentLat, currentLon) / 1000.0
            card.addView(TextView(this).apply { text = "${shop.kind} • %.1f km away".format(km) + if (shop.address.isNotBlank()) "\n${shop.address}" else "\nAddress not listed / पता उपलब्ध नहीं"; textSize = 13f; setTextColor(0xFF536273.toInt()); setPadding(0, 4, 0, 4) })
            val nav = Button(this).apply { text = "Directions / रास्ता देखें"; setOnClickListener { openDirections(shop) } }
            card.addView(nav)
            results.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })
        }
        results.addView(TextView(this).apply { text = "Data source: OpenStreetMap contributors. Listings, opening hours, stock and prices may be missing or outdated. Call the shop to verify.\nडेटा OpenStreetMap से है; उपलब्धता, समय और कीमत की पुष्टि दुकान से करें।"; textSize = 11f; setTextColor(0xFF667085.toInt()); setPadding(4, 10, 4, 18) })
    }

    private fun openDirections(shop: Shop) {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${shop.lat},${shop.lon}&travelmode=driving")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun showMap(items: List<Shop>) {
        val markers = items.joinToString(",") { "[${it.lat},${it.lon},${JSONObject.quote(it.name)}]" }
        val html = """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><style>html,body,#map{height:100%;margin:0}</style></head><body><div id='map'></div><script>var m=L.map('map').setView([$currentLat,$currentLon],13);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(m);L.marker([$currentLat,$currentLon]).addTo(m).bindPopup('Your location / आपकी लोकेशन');var a=[$markers];a.forEach(x=>L.marker([x[0],x[1]]).addTo(m).bindPopup(x[2]));L.circle([$currentLat,$currentLon],{radius:10000,color:'#2563eb',fillOpacity:.04}).addTo(m);</script></body></html>"""
        map.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    override fun onDestroy() { super.onDestroy(); executor.shutdownNow() }
}
