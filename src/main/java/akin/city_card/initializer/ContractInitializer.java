package akin.city_card.initializer;

import akin.city_card.contract.model.Contract;
import akin.city_card.contract.model.ContractType;
import akin.city_card.contract.repository.ContractRepository;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.security.repository.SecurityUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractInitializer implements CommandLineRunner {

    private final ContractRepository contractRepository;
    private final SecurityUserRepository securityUserRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeDefaultContracts();
    }

    private void initializeDefaultContracts() {
        log.info("Varsayılan sözleşmeler kontrol ediliyor...");

        // System admin kullanıcısını bul veya oluştur
        SecurityUser systemAdmin = getOrCreateSystemAdmin();

        // Üyelik Sözleşmesi
        createContractIfNotExists(
                ContractType.UYELIK_SOZLESMESI,
                "Üyelik Sözleşmesi",
                getMembershipContractContent(),
                "1.0",
                true,
                systemAdmin
        );

        // KVKK Aydınlatma Metni
        createContractIfNotExists(
                ContractType.AYDINLATMA_METNI,
                "KVKK Aydınlatma Metni",
                getKvkkIlluminationContent(),
                "1.0",
                true,
                systemAdmin
        );

        // Kişisel Verilerin İşlenmesine İlişkin Açık Rıza
        createContractIfNotExists(
                ContractType.VERI_ISLEME_IZNI,
                "Kişisel Verilerin İşlenmesine İlişkin Açık Rıza",
                getDataProcessingConsentContent(),
                "1.0",
                true,
                systemAdmin
        );

        // Gizlilik Politikası
        createContractIfNotExists(
                ContractType.GIZLILIK_POLITIKASI,
                "Gizlilik Politikası",
                getPrivacyPolicyContent(),
                "1.0",
                true,
                systemAdmin
        );

        log.info("Varsayılan sözleşmeler başarıyla kontrol edildi ve gerekirse oluşturuldu.");
    }

    private SecurityUser getOrCreateSystemAdmin() {
        Optional<SecurityUser> systemAdmin = securityUserRepository.findByUserNumber("+905333000016");
        
        if (systemAdmin.isPresent()) {
            return systemAdmin.get();
        }

        // System admin yoksa oluştur
        SecurityUser newSystemAdmin = SecurityUser.builder()
                .userNumber("+905333000016")
                .password("123456")
                .build();

        return securityUserRepository.save(newSystemAdmin);
    }

    private void createContractIfNotExists(ContractType type, String title, String content, 
                                         String version, boolean mandatory, SecurityUser createdBy) {
        
        // Bu tip için aktif sözleşme var mı kontrol et
        boolean exists = contractRepository.existsByTypeAndActive(type, true);
        
        if (!exists) {
            Contract contract = Contract.builder()
                    .title(title)
                    .content(content)
                    .version(version)
                    .type(type)
                    .mandatory(mandatory)
                    .active(true)
                    .createdBy(createdBy)
                    .build();

            contractRepository.save(contract);
            log.info("Yeni sözleşme oluşturuldu: {} - Versiyon: {}", title, version);
        } else {
            log.info("Sözleşme zaten mevcut: {}", title);
        }
    }

    private String getMembershipContractContent() {
        return """
                ÜYELIK SÖZLEŞMESİ
                
                Madde 1 - Taraflar
                Bu sözleşme, City Card uygulaması kullanıcısı ("Üye") ile City Card hizmeti sağlayıcısı ("Şirket") 
                arasında akdedilmiştir.
                
                Madde 2 - Sözleşmenin Konusu
                Bu sözleşme, Üye'nin City Card uygulaması ve platformunu kullanımına ilişkin hak ve 
                yükümlülükleri düzenler.
                
                Madde 3 - Üye Yükümlülükleri
                Üye, uygulamayı usulüne uygun kullanmayı, doğru bilgi vermeyi ve mevzuata uygun 
                davranmayı taahhüt eder.
                
                Madde 4 - Şirket Yükümlülükleri
                Şirket, hizmeti kesintisiz sunmaya, kişisel verileri korumaya ve kaliteli hizmet 
                vermeye çalışır.
                
                Madde 5 - Sözleşmenin Süresi
                Bu sözleşme, Üye'nin kaydının silinmesi veya hesabının kapatılması ile sona erer.
                
                Madde 6 - Uygulanacak Hukuk
                Bu sözleşmede Türkiye Cumhuriyeti kanunları uygulanır.
                """;
    }

    private String getKvkkIlluminationContent() {
        return """
                KVKK AYDINLATMA METNİ
                
                6698 sayılı Kişisel Verilerin Korunması Kanunu ("KVKK") kapsamında, kişisel 
                verilerinizin işlenmesine ilişkin olarak aşağıda belirtilen hususlarda 
                bilgilendirilmeniz amaçlanmaktadır.
                
                1. Veri Sorumlusu
                Kişisel verileriniz, City Card tarafından veri sorumlusu sıfatıyla işlenmektedir.
                
                2. Kişisel Verilerin İşlenme Amacı
                Kişisel verileriniz, üyelik işlemlerinin yürütülmesi, hizmet sunumu, 
                müşteri memnuniyetinin sağlanması ve yasal yükümlülüklerin yerine getirilmesi 
                amaçlarıyla işlenmektedir.
                
                3. İşlenen Kişisel Veri Kategorileri
                - Kimlik bilgileri (ad, soyad, TC kimlik numarası)
                - İletişim bilgileri (telefon, e-posta, adres)
                - Lokasyon bilgileri
                - İşlem geçmişi bilgileri
                
                4. Kişisel Verilerin Aktarılması
                Kişisel verileriniz, hizmet sağlayıcıları ve iş ortaklarımızla paylaşılabilir.
                
                5. Haklarınız
                KVKK kapsamında sahip olduğunuz hakları öğrenmek için gizlilik politikamızı 
                inceleyebilirsiniz.
                """;
    }

    private String getDataProcessingConsentContent() {
        return """
                KİŞİSEL VERİLERİN İŞLENMESİNE İLİŞKİN AÇIK RIZA
                
                6698 sayılı Kişisel Verilerin Korunması Kanunu ("KVKK") kapsamında, 
                aşağıda belirtilen konularda açık rızanızı almaktayız:
                
                1. Pazarlama ve Reklam Faaliyetleri
                Kişisel verilerinizin pazarlama ve reklam faaliyetleri kapsamında işlenmesine,
                size özel teklifler sunulmasına ve ürün/hizmet önerilerinde bulunulmasına 
                rıza gösteriyorum.
                
                2. Profilleme ve Analiz Faaliyetleri
                Davranışsal verilerinizin analiz edilmesi, kişiselleştirilmiş hizmetler 
                sunulması ve kullanıcı deneyiminin iyileştirilmesi amacıyla verilerinizin 
                işlenmesine rıza gösteriyorum.
                
                3. Üçüncü Taraflarla Veri Paylaşımı
                İş ortaklarımız ve hizmet sağlayıcılarımızla, yukarıda belirtilen amaçlarla 
                kişisel verilerimin paylaşılmasına rıza gösteriyorum.
                
                4. Lokasyon Verilerinin İşlenmesi
                Konum tabanlı hizmetler sunulması amacıyla lokasyon verilerimin işlenmesine 
                rıza gösteriyorum.
                
                Bu rızamı istediğim zaman geri alabileceğimi biliyorum.
                """;
    }

    private String getPrivacyPolicyContent() {
        return """
                GİZLİLİK POLİTİKASI
                
                City Card olarak, kişisel verilerinizin güvenliği bizim önceliğimizdir. 
                Bu politika, kişisel verilerinizin nasıl toplandığını, kullanıldığını ve 
                korunduğunu açıklamaktadır.
                
                1. Toplanan Bilgiler
                - Kayıt sırasında verdiğiniz kişisel bilgiler
                - Uygulama kullanımınızla ilgili teknik bilgiler
                - Lokasyon bilgileri (izin vermeniz halinde)
                - İşlem geçmişi ve tercihleriniz
                
                2. Bilgilerin Kullanımı
                Toplanan bilgiler şu amaçlarla kullanılır:
                - Hizmet sunumu ve iyileştirme
                - Müşteri desteği sağlama
                - Güvenlik ve dolandırıcılık önleme
                - Yasal yükümlülüklerin yerine getirilmesi
                
                3. Bilgi Paylaşımı
                Kişisel bilgileriniz, yasal zorunluluklar dışında üçüncü taraflarla 
                paylaşılmaz. Hizmet sağlayıcılarımızla sadece gerekli olan bilgiler 
                paylaşılır.
                
                4. Veri Güvenliği
                Verileriniz, endüstri standardı güvenlik önlemleriyle korunmaktadır.
                
                5. Haklarınız
                KVKK kapsamında bilgilerinize erişim, düzeltme, silme ve aktarım haklarına 
                sahipsiniz.
                
                6. İletişim
                Gizlilik politikası ile ilgili sorularınız için bizimle iletişime geçebilirsiniz.
                """;
    }
}