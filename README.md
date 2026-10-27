# HomeCam

Purane phone ko ghar ka camera banayen aur dusre phone se photo, live video aur awaaz dekhen/sunen.

## 1. Firebase (ek baar)

1. Firebase console > apna project `cctv-35395` kholen.
2. Build > Realtime Database > Create database. Location jo marzi chunen.
3. Rules tab mein `database.rules.json` ka text paste karke Publish karen.
4. Project settings > Your apps > Android app (`com.homecam.app`) > `google-services.json` DOBARA download karen
   aur `app/google-services.json` ko is se badal den. (Database banane ke baad wali file mein database ka address hota hai.)

## 2. GitHub se APK banana

1. Naya PRIVATE repository banayen (is mein aap ke Firebase ki keys hain).
2. Is folder ki saari files upload karen (`.github` folder samet).
3. Repository > Actions > Build APK > Run workflow. Taqreeban 5 se 10 minute lagte hain.
4. Kamyab hone par us run ke neeche Artifacts mein `homecam-apk` download karen, zip kholen, `app-debug.apk` mil jayegi.

## 3. Purane phone par (screen laga kar, 2 minute)

1. `app-debug.apk` install karen (unknown sources allow karni paregi).
2. App kholen. Camera, mic, notification aur battery ke popup aayenge, sab par "Allow" karen.
3. Camera apne aap hamesha chalu rehta hai, koi on/off button nahi hai. Chahen to phone ka naam badal kar "Naam save karo" dabayen.
4. Agar koi popup chhoot jaye to "Permissions do" dabayen.
5. Settings > Developer options > Stay awake on karen (agar milay).
6. WiFi lagi ho, charger laga den, phir screen hata den.

Phone restart ho to camera khud chalu ho jata hai. Ghar ka code app ke andar pehle se bana hua hai (`Config.kt`).

## 4. Dekhna (viewer.html)

1. `viewer.html` kisi bhi phone/computer ke browser mein kholen.
2. Saare camere naam se list mein aate hain. Jis par click karen uska camera khulta hai.
3. Buttons: Photo lo, Live video, Awaaz suno, Front/Back camera, Flash chalu/band (sirf peeche wale camera mein), Awaaz record karo.
4. "Awaaz tez" slider se door ki awaaz barhayen (1x se 10x).
5. Photo lene par wo gallery mein khud aa jati hai. Neeche "Phone mein save karo" ya "Delete karo" milta hai.
6. Gallery mein saari photos aur recordings dikhti hain. Har ek ko Save (download) ya Delete kar sakte hain, ya "Sab delete karo".

Gallery sirf usi browser mein mehfooz hoti hai jis mein aap ne photo li. Browser ka data saaf karne par ye mit jati hai, to zaroori photos Save kar len.

## Haddein

- `viewer.html` aur `Config.kt` mein ghar ka code likha hai. Ye password hai. `viewer.html` ko public website par na rakhen aur kisi ko na den.
- Ghar ke WiFi par aur aam mobile data par aksar chalta hai. Kuch networks par TURN server chahiye hota hai.
- Agar Samsung ki battery saving app band kar de to Settings > Battery mein HomeCam ko "unrestricted" karen.
