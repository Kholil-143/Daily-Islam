# হোম স্ক্রিন উইজেট (Android AppWidget)

এই ফোল্ডারে Muslim Path অ্যাপের নেটিভ হোম-স্ক্রিন উইজেটের সম্পূর্ণ কোড আছে।
APK বানানোর সময় GitHub Actions workflow নিজে থেকেই `install-widget.sh`
চালিয়ে এই ফাইলগুলো Capacitor-এর বানানো `android/` প্রজেক্টে বসিয়ে দেয়
এবং `AndroidManifest.xml`-এ উইজেটটি রেজিস্টার করে। হাতে কিছু করতে হয় না।

## উইজেটে যা দেখায়

- লাইভ ঘড়ি (সেকেন্ডসহ, TextClock)
- ইংরেজি, বাংলা ও হিজরি — তিনটি তারিখ
- বেছে নেওয়া জেলার আজকের নামাজের সময় (ফজর, সূর্যোদয়, যোহর, আসর, মাগরিব, এশা)
- পরের ওয়াক্ত ও বাকি সময় (প্রতি আপডেটে হিসাব করা)
- রিফ্রেশ বোতাম — চাপ দিলেই সব তথ্য তখনই আপডেট হয়
- উইজেটে চাপ দিলে অ্যাপ খোলে

## ফাইলের তালিকা

- `java/com/kholil/muslimpath/widget/PrayerWidgetProvider.java` — মূল উইজেট
- `java/com/kholil/muslimpath/widget/WidgetConfigActivity.java` — জেলা বাছাইয়ের পর্দা
- `java/com/kholil/muslimpath/widget/Districts.java` — বাংলাদেশের ৬৪ জেলার নাম ও অক্ষাংশ/দ্রাঘিমাংশ
- `java/com/kholil/muslimpath/widget/PrayerTimes.java` — নামাজের সময়ের হিসাব
  (সৌর অবস্থানের প্রমিত অ্যালগরিদম; ফজর/এশা ১৮°, আসর হানাফি পদ্ধতিতে)
- `java/com/kholil/muslimpath/widget/BengaliDate.java` — বাংলা তারিখ ও বাংলা সংখ্যা
- `res/layout/widget_prayer.xml` — উইজেটের নকশা
- `res/layout/widget_config.xml` — জেলা বাছাইয়ের পর্দার নকশা
- `res/xml/prayer_widget_info.xml` — উইজেটের মেটা তথ্য (৩০ মিনিট পরপর অটো আপডেট)
- `res/drawable/widget_bg.xml`, `res/values/widget_strings.xml` — পটভূমি ও লেখা
- `install-widget.sh` — বিল্ডের সময় উইজেট বসানোর স্ক্রিপ্ট

## ব্যবহার

অ্যাপ ইনস্টল করার পর ফোনের হোম স্ক্রিনে ফাঁকা জায়গায় চেপে ধরে
**Widgets → Muslim Path** বেছে উইজেটটি বসান। বসানোর সময় জেলা বাছাইয়ের
পর্দা আসবে — নিজের জেলা বেছে নিলেই সেই জেলার নামাজের সময় দেখাবে।
