# Implementor
Генерация простой имплементации интерфейса. Также присутствует возможность упаковки имплементации в jar файл.

## Пример интерфейса:
```java
public interface Config {

    String readFromFile();

    boolean checkSomething();

    void test();

}
```

## Имплементация интерфейса Config:
```java
public class ConfigImpl implements Config {

    @Override
    public void test() {
        return ;
    }

    @Override
    public java.lang.String readFromFile() {
        return null;
    }

    @Override
    public boolean checkSomething() {
        return false;
    }
}
```
